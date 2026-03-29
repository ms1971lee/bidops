package com.bidops.domain.checklist.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.domain.checklist.dto.*;
import com.bidops.domain.checklist.entity.ChecklistItem;
import com.bidops.domain.checklist.entity.ChecklistReview;
import com.bidops.domain.checklist.entity.SubmissionChecklist;
import com.bidops.domain.checklist.enums.ChecklistItemStatus;
import com.bidops.domain.checklist.enums.RiskLevel;
import com.bidops.domain.checklist.repository.ChecklistItemRepository;
import com.bidops.domain.checklist.repository.ChecklistReviewRepository;
import com.bidops.domain.checklist.repository.SubmissionChecklistRepository;
import com.bidops.domain.checklist.service.ChecklistService;
import org.springframework.data.domain.PageRequest;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChecklistServiceImpl implements ChecklistService {

    private final SubmissionChecklistRepository checklistRepository;
    private final ChecklistItemRepository itemRepository;
    private final ChecklistReviewRepository reviewRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectActivityService activityService;

    // ── 체크리스트 묶음 ─────────────────────────────────────────────────────

    @Override
    public List<ChecklistDto> listChecklists(String projectId) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_VIEW);
        return checklistRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(c -> ChecklistDto.from(c,
                        itemRepository.countByChecklistId(c.getId()),
                        itemRepository.countByChecklistIdAndCurrentStatus(c.getId(), ChecklistItemStatus.DONE)))
                .toList();
    }

    @Override
    @Transactional
    public ChecklistDto createChecklist(String projectId, ChecklistCreateRequest request) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_EDIT);

        SubmissionChecklist checklist = SubmissionChecklist.builder()
                .projectId(projectId)
                .checklistType(request.getChecklistType())
                .title(request.getTitle())
                .build();

        SubmissionChecklist saved = checklistRepository.save(checklist);
        activityService.record(projectId, ActivityType.CHECKLIST_CREATED,
                "체크리스트 생성: " + saved.getTitle(),
                com.bidops.auth.SecurityUtils.currentUserId(), saved.getId(), "checklist", null);
        return ChecklistDto.from(saved, 0, 0);
    }

    @Override
    public ChecklistDto getChecklist(String projectId, String checklistId) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_VIEW);
        SubmissionChecklist c = findChecklistOrThrow(projectId, checklistId);
        return ChecklistDto.from(c,
                itemRepository.countByChecklistId(c.getId()),
                itemRepository.countByChecklistIdAndCurrentStatus(c.getId(), ChecklistItemStatus.DONE));
    }

    // ── 체크리스트 항목 ─────────────────────────────────────────────────────

    @Override
    public List<ChecklistItemDto> listItems(String projectId, String checklistId,
                                             ChecklistItemStatus status,
                                             RiskLevel riskLevel,
                                             Boolean mandatory,
                                             String requirementId,
                                             String ownerUserId,
                                             String keyword) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_VIEW);
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return itemRepository.search(checklistId, status, riskLevel, mandatory, requirementId, ownerUserId, kw)
                .stream()
                .map(ChecklistItemDto::from)
                .toList();
    }

    @Override
    @Transactional
    public ChecklistItemDto createItem(String projectId, String checklistId, ChecklistItemCreateRequest request) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_EDIT);

        checklistRepository.findById(checklistId)
                .orElseThrow(() -> BidOpsException.notFound("체크리스트"));

        long seq = itemRepository.countByChecklistId(checklistId) + 1;
        String itemCode = String.format("CHK-%03d", seq);

        ChecklistItem item = ChecklistItem.builder()
                .checklistId(checklistId)
                .itemCode(itemCode)
                .itemText(request.getItemText())
                .mandatoryFlag(request.getMandatoryFlag() != null ? request.getMandatoryFlag() : false)
                .dueHint(request.getDueHint())
                .riskLevel(request.getRiskLevel() != null ? request.getRiskLevel() : RiskLevel.NONE)
                .riskNote(request.getRiskNote())
                .linkedRequirementId(request.getLinkedRequirementId())
                .sourceExcerptId(request.getSourceExcerptId())
                .build();

        ChecklistItem saved = itemRepository.save(item);
        activityService.record(projectId, ActivityType.CHECKLIST_ITEM_CREATED,
                "체크리스트 항목 추가: " + saved.getItemCode(),
                com.bidops.auth.SecurityUtils.currentUserId(), saved.getId(), "checklist", null);
        return ChecklistItemDto.from(saved);
    }

    @Override
    public ChecklistItemDto getItem(String projectId, String checklistId, String itemId) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_VIEW);
        return ChecklistItemDto.from(findItemOrThrow(checklistId, itemId));
    }

    @Override
    @Transactional
    public ChecklistItemDto updateItem(String projectId, String checklistId, String itemId,
                                        ChecklistItemUpdateRequest request) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_EDIT);
        ChecklistItem item = findItemOrThrow(checklistId, itemId);
        String actor = com.bidops.auth.SecurityUtils.currentUserId();

        // 담당자 변경 이력
        if (request.getOwnerUserId() != null && !request.getOwnerUserId().equals(item.getOwnerUserId())) {
            recordReview(itemId, "OWNER_CHANGED", item.getOwnerUserId(), request.getOwnerUserId(), null, actor);
            item.assignOwner(request.getOwnerUserId());
        }
        // 메모 변경 이력
        if (request.getActionComment() != null) {
            recordReview(itemId, "COMMENT_ADDED", null, null, request.getActionComment(), actor);
            item.setActionComment(request.getActionComment());
        }

        item.update(request.getItemText(), request.getMandatoryFlag(),
                    request.getDueHint(), request.getRiskLevel(),
                    request.getRiskNote(), request.getLinkedRequirementId());

        String detail = null;
        if (request.getOwnerUserId() != null) detail = "담당자: " + request.getOwnerUserId();
        if (request.getActionComment() != null) detail = (detail != null ? detail + " | " : "") + "메모: " + request.getActionComment();
        activityService.record(projectId, ActivityType.CHECKLIST_ITEM_UPDATED,
                "체크리스트 항목 수정: " + item.getItemCode(), actor, itemId, "checklist", detail);
        return ChecklistItemDto.from(item);
    }

    @Override
    @Transactional
    public ChecklistItemDto changeItemStatus(String projectId, String checklistId, String itemId,
                                              ChecklistItemStatusChangeRequest request) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_EDIT);
        ChecklistItem item = findItemOrThrow(checklistId, itemId);
        String actor = com.bidops.auth.SecurityUtils.currentUserId();
        String before = item.getCurrentStatus().name();
        item.changeStatus(request.getStatus());
        recordReview(itemId, "STATUS_CHANGED", before, request.getStatus().name(), null, actor);
        activityService.record(projectId, ActivityType.CHECKLIST_ITEM_STATUS_CHANGED,
                "체크리스트 항목 상태: " + item.getItemCode() + " → " + request.getStatus(),
                actor, itemId, "checklist", before + " → " + request.getStatus().name());
        return ChecklistItemDto.from(item);
    }

    @Override
    public List<ChecklistReviewDto> listReviews(String projectId, String checklistId, String itemId, int limit) {
        requirePermission(projectId, ProjectPermission.CHECKLIST_VIEW);
        if (limit <= 0) {
            return reviewRepository.findByChecklistItemIdOrderByCreatedAtDesc(itemId)
                    .stream().map(ChecklistReviewDto::from).toList();
        }
        return reviewRepository.findByChecklistItemIdOrderByCreatedAtDesc(itemId, PageRequest.of(0, limit))
                .stream().map(ChecklistReviewDto::from).toList();
    }

    // ── internal ────────────────────────────────────────────────────────────

    private void recordReview(String itemId, String changeType, String before, String after, String comment, String actor) {
        reviewRepository.save(ChecklistReview.builder()
                .checklistItemId(itemId)
                .changeType(changeType)
                .beforeValue(before)
                .afterValue(after)
                .comment(comment)
                .actorUserId(actor)
                .build());
    }

    private SubmissionChecklist findChecklistOrThrow(String projectId, String checklistId) {
        return checklistRepository.findByIdAndProjectId(checklistId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("체크리스트"));
    }

    private ChecklistItem findItemOrThrow(String checklistId, String itemId) {
        return itemRepository.findByIdAndChecklistId(itemId, checklistId)
                .orElseThrow(() -> BidOpsException.notFound("체크리스트 항목"));
    }

    private void requirePermission(String projectId, ProjectPermission permission) {
        authorizationService.requirePermission(projectId, com.bidops.auth.SecurityUtils.currentUserId(), permission);
    }
}
