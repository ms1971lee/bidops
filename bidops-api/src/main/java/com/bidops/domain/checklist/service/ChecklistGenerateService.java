package com.bidops.domain.checklist.service;

import com.bidops.auth.SecurityUtils;
import com.bidops.domain.checklist.dto.ChecklistGenerateResponse;
import com.bidops.domain.checklist.entity.ChecklistItem;
import com.bidops.domain.checklist.entity.SubmissionChecklist;
import com.bidops.domain.checklist.enums.ChecklistType;
import com.bidops.domain.checklist.enums.RiskLevel;
import com.bidops.domain.checklist.repository.ChecklistItemRepository;
import com.bidops.domain.checklist.repository.SubmissionChecklistRepository;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.repository.RequirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 체크리스트 항목 자동 생성 서비스.
 *
 * 프로젝트의 요구사항을 기준으로 SUBMISSION 체크리스트를 생성하고
 * 각 요구사항에 대응하는 항목을 자동 삽입한다.
 *
 * 정책:
 * - 최초 호출 시: 새 체크리스트 + 항목 생성
 * - 중복 호출 시: 이미 linkedRequirementId로 연결된 항목은 skip
 * - mandatory/evidenceRequired 요구사항 → HIGH 위험도
 * - queryNeeded 요구사항 → MEDIUM 위험도
 * - 일반 요구사항 → LOW 위험도
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChecklistGenerateService {

    private final RequirementRepository requirementRepository;
    private final SubmissionChecklistRepository checklistRepository;
    private final ChecklistItemRepository itemRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectActivityService activityService;

    @Transactional
    public ChecklistGenerateResponse generate(String projectId) {
        authorizationService.requirePermission(
                projectId, SecurityUtils.currentUserId(), ProjectPermission.CHECKLIST_EDIT);

        // 요구사항 조회 (archived 제외)
        List<Requirement> requirements = requirementRepository.search(
                projectId, null, null, null, null, null, null, null, null,
                Pageable.unpaged()).getContent();

        if (requirements.isEmpty()) {
            return ChecklistGenerateResponse.builder()
                    .checklistId(null)
                    .createdCount(0)
                    .skippedCount(0)
                    .createdItemIds(List.of())
                    .build();
        }

        // 기존 SUBMISSION 체크리스트가 있으면 재사용, 없으면 생성
        SubmissionChecklist checklist = checklistRepository
                .findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(c -> c.getChecklistType() == ChecklistType.SUBMISSION)
                .findFirst()
                .orElseGet(() -> {
                    SubmissionChecklist newCl = SubmissionChecklist.builder()
                            .projectId(projectId)
                            .checklistType(ChecklistType.SUBMISSION)
                            .title("요구사항 기반 자동 생성 체크리스트")
                            .build();
                    return checklistRepository.save(newCl);
                });

        String checklistId = checklist.getId();
        long baseSeq = itemRepository.countByChecklistId(checklistId);
        List<String> createdIds = new ArrayList<>();
        int skipped = 0;

        for (Requirement req : requirements) {
            // 이미 해당 요구사항에 연결된 항목이 있으면 skip
            if (itemRepository.existsByChecklistIdAndLinkedRequirementId(checklistId, req.getId())) {
                skipped++;
                continue;
            }

            baseSeq++;
            String itemCode = String.format("CHK-%03d", baseSeq);

            ChecklistItem item = ChecklistItem.builder()
                    .checklistId(checklistId)
                    .itemCode(itemCode)
                    .itemText(buildItemText(req))
                    .mandatoryFlag(req.isMandatoryFlag())
                    .riskLevel(determineRiskLevel(req))
                    .riskNote(buildRiskNote(req))
                    .linkedRequirementId(req.getId())
                    .build();

            ChecklistItem saved = itemRepository.save(item);
            createdIds.add(saved.getId());
        }

        if (!createdIds.isEmpty()) {
            activityService.record(projectId, ActivityType.CHECKLIST_ITEM_CREATED,
                    "체크리스트 항목 일괄 생성: " + createdIds.size() + "건",
                    SecurityUtils.currentUserId(), checklistId, "checklist",
                    "생성 " + createdIds.size() + " / 건너뜀 " + skipped);
        }

        log.info("[ChecklistGenerate] projectId={} checklistId={} created={} skipped={}",
                projectId, checklistId, createdIds.size(), skipped);

        return ChecklistGenerateResponse.builder()
                .checklistId(checklistId)
                .createdCount(createdIds.size())
                .skippedCount(skipped)
                .createdItemIds(createdIds)
                .build();
    }

    // ── 텍스트/위험도 결정 ─────────────────────────────────────────

    private String buildItemText(Requirement req) {
        String code = req.getRequirementCode() != null ? req.getRequirementCode() : "";
        String title = req.getTitle() != null ? req.getTitle() : "";
        String category = req.getCategory() != null ? "[" + req.getCategory().name() + "] " : "";
        return category + code + " " + title;
    }

    private RiskLevel determineRiskLevel(Requirement req) {
        if (req.isMandatoryFlag() && req.isEvidenceRequiredFlag()) {
            return RiskLevel.HIGH;
        }
        if (req.isMandatoryFlag() || req.isQueryNeeded()) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private String buildRiskNote(Requirement req) {
        List<String> notes = new ArrayList<>();
        if (req.isMandatoryFlag()) notes.add("필수");
        if (req.isEvidenceRequiredFlag()) notes.add("증빙 필요");
        if (req.isQueryNeeded()) notes.add("질의 필요");
        return notes.isEmpty() ? null : String.join(", ", notes);
    }
}
