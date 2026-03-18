package com.bidops.domain.checklist.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.domain.checklist.dto.*;
import com.bidops.domain.checklist.entity.ChecklistItem;
import com.bidops.domain.checklist.entity.SubmissionChecklist;
import com.bidops.domain.checklist.enums.ChecklistItemStatus;
import com.bidops.domain.checklist.enums.RiskLevel;
import com.bidops.domain.checklist.repository.ChecklistItemRepository;
import com.bidops.domain.checklist.repository.SubmissionChecklistRepository;
import com.bidops.domain.checklist.service.ChecklistService;
import com.bidops.domain.project.repository.ProjectRepository;
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
    private final ProjectRepository projectRepository;

    // ── 체크리스트 묶음 ─────────────────────────────────────────────────────

    @Override
    public List<ChecklistDto> listChecklists(String projectId) {
        validateProject(projectId);
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
        validateProject(projectId);

        SubmissionChecklist checklist = SubmissionChecklist.builder()
                .projectId(projectId)
                .checklistType(request.getChecklistType())
                .title(request.getTitle())
                .build();

        SubmissionChecklist saved = checklistRepository.save(checklist);
        return ChecklistDto.from(saved, 0, 0);
    }

    @Override
    public ChecklistDto getChecklist(String projectId, String checklistId) {
        SubmissionChecklist c = findChecklistOrThrow(projectId, checklistId);
        return ChecklistDto.from(c,
                itemRepository.countByChecklistId(c.getId()),
                itemRepository.countByChecklistIdAndCurrentStatus(c.getId(), ChecklistItemStatus.DONE));
    }

    // ── 체크리스트 항목 ─────────────────────────────────────────────────────

    @Override
    public List<ChecklistItemDto> listItems(String checklistId,
                                             ChecklistItemStatus status,
                                             RiskLevel riskLevel,
                                             Boolean mandatory,
                                             String requirementId) {
        return itemRepository.search(checklistId, status, riskLevel, mandatory, requirementId)
                .stream()
                .map(ChecklistItemDto::from)
                .toList();
    }

    @Override
    @Transactional
    public ChecklistItemDto createItem(String checklistId, ChecklistItemCreateRequest request) {
        // 체크리스트 존재 확인
        checklistRepository.findById(checklistId)
                .orElseThrow(() -> BidOpsException.notFound("체크리스트"));

        // itemCode 자동 채번
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

        return ChecklistItemDto.from(itemRepository.save(item));
    }

    @Override
    public ChecklistItemDto getItem(String checklistId, String itemId) {
        return ChecklistItemDto.from(findItemOrThrow(checklistId, itemId));
    }

    @Override
    @Transactional
    public ChecklistItemDto updateItem(String checklistId, String itemId,
                                        ChecklistItemUpdateRequest request) {
        ChecklistItem item = findItemOrThrow(checklistId, itemId);
        item.update(request.getItemText(), request.getMandatoryFlag(),
                    request.getDueHint(), request.getRiskLevel(),
                    request.getRiskNote(), request.getLinkedRequirementId());
        return ChecklistItemDto.from(item);
    }

    @Override
    @Transactional
    public ChecklistItemDto changeItemStatus(String checklistId, String itemId,
                                              ChecklistItemStatusChangeRequest request) {
        ChecklistItem item = findItemOrThrow(checklistId, itemId);
        item.changeStatus(request.getStatus());
        return ChecklistItemDto.from(item);
    }

    // ── internal ────────────────────────────────────────────────────────────

    private SubmissionChecklist findChecklistOrThrow(String projectId, String checklistId) {
        return checklistRepository.findByIdAndProjectId(checklistId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("체크리스트"));
    }

    private ChecklistItem findItemOrThrow(String checklistId, String itemId) {
        return itemRepository.findByIdAndChecklistId(itemId, checklistId)
                .orElseThrow(() -> BidOpsException.notFound("체크리스트 항목"));
    }

    private void validateProject(String projectId) {
        projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> BidOpsException.notFound("프로젝트"));
    }
}
