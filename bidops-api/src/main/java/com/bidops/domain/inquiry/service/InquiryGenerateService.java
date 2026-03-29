package com.bidops.domain.inquiry.service;

import com.bidops.auth.SecurityUtils;
import com.bidops.domain.inquiry.dto.InquiryGenerateResponse;
import com.bidops.domain.inquiry.entity.Inquiry;
import com.bidops.domain.inquiry.enums.InquiryPriority;
import com.bidops.domain.inquiry.repository.InquiryRepository;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.repository.RequirementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 질의 초안 일괄 생성 서비스.
 *
 * queryNeeded=true인 요구사항에서 질의 초안을 자동 생성한다.
 * 이미 연결된 질의가 있으면 건너뛴다.
 *
 * TODO: AI 기반 질의 문안 생성 시 buildQuestionText()를 확장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryGenerateService {

    private final RequirementRepository requirementRepository;
    private final InquiryRepository inquiryRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectActivityService activityService;

    @Transactional
    public InquiryGenerateResponse generate(String projectId) {
        authorizationService.requirePermission(
                projectId, SecurityUtils.currentUserId(), ProjectPermission.INQUIRY_EDIT);

        List<Requirement> candidates = requirementRepository.findByProjectIdAndQueryNeededTrue(projectId);

        long baseSeq = inquiryRepository.countByProjectId(projectId);
        List<String> createdIds = new ArrayList<>();
        int skipped = 0;

        for (Requirement req : candidates) {
            if (inquiryRepository.existsByProjectIdAndRequirementId(projectId, req.getId())) {
                skipped++;
                continue;
            }

            baseSeq++;
            String code = String.format("INQ-%03d", baseSeq);

            Inquiry inquiry = Inquiry.builder()
                    .projectId(projectId)
                    .inquiryCode(code)
                    .title(buildTitle(req))
                    .questionText(buildQuestionText(req))
                    .reasonNote(buildReasonNote(req))
                    .priority(InquiryPriority.MEDIUM)
                    .requirementId(req.getId())
                    .build();

            Inquiry saved = inquiryRepository.save(inquiry);
            createdIds.add(saved.getId());
        }

        if (!createdIds.isEmpty()) {
            activityService.record(projectId, ActivityType.INQUIRY_CREATED,
                    "질의 초안 일괄 생성: " + createdIds.size() + "건",
                    SecurityUtils.currentUserId(), null, "inquiry",
                    "생성 " + createdIds.size() + " / 건너뜀 " + skipped);
        }

        log.info("[InquiryGenerate] projectId={} created={} skipped={}",
                projectId, createdIds.size(), skipped);

        return InquiryGenerateResponse.builder()
                .createdCount(createdIds.size())
                .skippedCount(skipped)
                .createdInquiryIds(createdIds)
                .build();
    }

    // ── 텍스트 생성 (AI 확장 포인트) ──────────────────────────────────

    /** 질의 제목. TODO: AI 요약으로 교체 가능 */
    private String buildTitle(Requirement req) {
        String code = req.getRequirementCode() != null ? req.getRequirementCode() : "";
        String title = req.getTitle() != null ? req.getTitle() : "";
        String result = "[질의] " + code + ": " + title;
        return result.length() > 290 ? result.substring(0, 290) : result;
    }

    /** 질의 내용. TODO: AI 질의 문안 생성으로 교체 가능 */
    private String buildQuestionText(Requirement req) {
        return req.getOriginalText() != null ? req.getOriginalText() : req.getTitle();
    }

    /** 질의 사유. */
    private String buildReasonNote(Requirement req) {
        return "AI 분석에서 질의 필요로 판단된 항목 (" + req.getRequirementCode() + ")";
    }
}
