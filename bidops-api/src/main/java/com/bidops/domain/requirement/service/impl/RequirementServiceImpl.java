package com.bidops.domain.requirement.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.response.ListData;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import com.bidops.domain.requirement.dto.*;
import com.bidops.domain.requirement.entity.*;
import com.bidops.domain.requirement.enums.*;
import com.bidops.domain.requirement.repository.*;
import com.bidops.domain.requirement.service.RequirementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequirementServiceImpl implements RequirementService {

    private final RequirementRepository        requirementRepository;
    private final RequirementInsightRepository insightRepository;
    private final RequirementReviewRepository  reviewRepository;
    private final RequirementSourceRepository  sourceRepository;
    private final SourceExcerptRepository      excerptRepository;
    private final ProjectAuthorizationService   authorizationService;
    private final ProjectActivityService       activityService;
    private final ObjectMapper                 objectMapper;

    // ── 목록 ─────────────────────────────────────────────────────────────────
    @Override
    public ListData<RequirementDto> listRequirements(
            String projectId,
            RequirementCategory category, Boolean mandatory, Boolean evidenceRequired,
            RequirementAnalysisStatus analysisStatus, RequirementReviewStatus reviewStatus,
            FactLevel factLevel, Boolean queryNeeded, String keyword,
            int page, int size) {

        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);
        PageRequest pageable = PageRequest.of(
                page - 1, size, Sort.by(Sort.Direction.ASC, "requirementCode"));

        Page<RequirementDto> result = requirementRepository
                .search(projectId, category, mandatory, evidenceRequired,
                        analysisStatus, reviewStatus, factLevel, queryNeeded, keyword, pageable)
                .map(RequirementDto::from);

        return new ListData<>(result.getContent(), result.getTotalElements());
    }

    // ── 상세 (requirement + insight + review 조합) ────────────────────────────
    @Override
    public RequirementDetailDto getRequirement(String projectId, String requirementId) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);
        Requirement req = findOrThrow(projectId, requirementId);

        RequirementInsightDto insight = insightRepository.findByRequirementId(requirementId)
                .map(RequirementInsightDto::from)
                .orElse(RequirementInsightDto.empty(requirementId));

        RequirementReviewDto review = reviewRepository.findByRequirementId(requirementId)
                .map(RequirementReviewDto::from)
                .orElse(RequirementReviewDto.empty(requirementId));

        return RequirementDetailDto.of(RequirementDto.from(req), insight, review);
    }

    // ── 요구사항 기본 정보 수정 ────────────────────────────────────────────────
    @Override
    @Transactional
    public RequirementDetailDto updateRequirement(String projectId, String requirementId,
                                                  RequirementUpdateRequest request) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_EDIT);
        Requirement req = findOrThrow(projectId, requirementId);
        req.update(request.getTitle(), request.getCategory(),
                   request.getMandatoryFlag(), request.getEvidenceRequiredFlag(),
                   request.getAnalysisStatus());
        activityService.record(projectId, ActivityType.REQUIREMENT_UPDATED,
                "요구사항 수정: " + req.getRequirementCode(),
                currentUserId(), requirementId, "requirement", null);
        return getRequirement(projectId, requirementId);
    }

    // ── 원문 근거 (SourceExcerpt 기반 동적 조합) ──────────────────────────────
    @Override
    public RequirementSourcesDto getSources(String projectId, String requirementId) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);
        findOrThrow(projectId, requirementId);

        // Step1: RequirementSource 조회 (linkType 포함)
        var sources = sourceRepository.findByRequirementIdOrderByLinkTypeAsc(requirementId);
        if (sources.isEmpty()) {
            return RequirementSourcesDto.from(java.util.Collections.emptyList());
        }

        // Step2: SourceExcerpt 일괄 조회
        List<String> excerptIds = sources.stream()
                .map(rs -> rs.getSourceExcerptId()).distinct().toList();
        var excerptMap = excerptRepository.findAllByIdInOrdered(excerptIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        com.bidops.domain.document.entity.SourceExcerpt::getId,
                        e -> e, (a, b) -> a));

        return RequirementSourcesDto.from(sources, excerptMap);
    }

    // ── AI 분석 조회 ─────────────────────────────────────────────────────────
    @Override
    public RequirementInsightDto getInsight(String projectId, String requirementId) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);
        findOrThrow(projectId, requirementId);
        return insightRepository.findByRequirementId(requirementId)
                .map(RequirementInsightDto::from)
                .orElse(RequirementInsightDto.empty(requirementId));
    }

    // ── AI 분석 수정 ─────────────────────────────────────────────────────────
    @Override
    @Transactional
    public RequirementInsightDto updateInsight(String projectId, String requirementId,
                                               RequirementAnalysisUpdateRequest request) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_EDIT);
        findOrThrow(projectId, requirementId);

        RequirementInsight insight = insightRepository.findByRequirementId(requirementId)
                .orElseGet(() -> RequirementInsight.builder()
                        .requirementId(requirementId)
                        .build());

        insight.update(
                request.getFactSummary(),
                request.getInterpretationSummary(),
                request.getIntentSummary(),
                request.getProposalPoint(),
                request.getImplementationApproach(),
                toJson(request.getExpectedDeliverables()),
                request.getDifferentiationPoint(),
                toJson(request.getRiskNote()),
                request.getQueryNeeded(),
                request.getFactLevel()
        );

        RequirementInsight saved = insightRepository.save(insight);
        activityService.record(projectId, ActivityType.REQUIREMENT_INSIGHT_UPDATED,
                "AI 분석 수정: " + requirementId,
                currentUserId(), requirementId, "requirement", null);
        return RequirementInsightDto.from(saved);
    }

    // ── 사람 검토 조회 ───────────────────────────────────────────────────────
    @Override
    public RequirementReviewDto getReview(String projectId, String requirementId) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_VIEW);
        findOrThrow(projectId, requirementId);
        return reviewRepository.findByRequirementId(requirementId)
                .map(RequirementReviewDto::from)
                .orElse(RequirementReviewDto.empty(requirementId));
    }

    // ── 검토 상태 변경 (OWNER만 가능: 승인 행위) ──────────────────────────────
    @Override
    @Transactional
    public RequirementReviewDto changeReviewStatus(String projectId, String requirementId,
                                                   RequirementReviewStatusChangeRequest request) {
        requirePermission(projectId, ProjectPermission.REQUIREMENT_APPROVE);
        Requirement req = findOrThrow(projectId, requirementId);

        RequirementReview review = reviewRepository.findByRequirementId(requirementId)
                .orElseGet(() -> RequirementReview.builder()
                        .requirementId(requirementId)
                        .build());

        String beforeStatus = review.getReviewStatus().name();
        review.changeStatus(request.getReviewStatus(), request.getReviewComment(), currentUserId());
        RequirementReview saved = reviewRepository.save(review);

        // Requirement 테이블에 비정규화 동기화 (목록 필터 성능용)
        req.syncReviewStatus(request.getReviewStatus());

        // before/after + comment 기록
        String detail = beforeStatus + " → " + request.getReviewStatus().name();
        if (request.getReviewComment() != null && !request.getReviewComment().isBlank()) {
            detail += " | " + request.getReviewComment();
        }
        activityService.record(projectId, ActivityType.REQUIREMENT_REVIEW_CHANGED,
                "요구사항 검토: " + req.getRequirementCode() + " " + beforeStatus + " → " + request.getReviewStatus(),
                currentUserId(), requirementId, "requirement", detail);

        return RequirementReviewDto.from(saved);
    }

    // ── internal helpers ─────────────────────────────────────────────────────
    private Requirement findOrThrow(String projectId, String requirementId) {
        return requirementRepository.findByIdAndProjectId(requirementId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("요구사항"));
    }

    private void requirePermission(String projectId, ProjectPermission permission) {
        authorizationService.requirePermission(projectId, com.bidops.auth.SecurityUtils.currentUserId(), permission);
    }

    private String currentUserId() {
        return com.bidops.auth.SecurityUtils.currentUserId();
    }

    private String toJson(java.util.List<String> list) {
        if (list == null) return null;
        try { return objectMapper.writeValueAsString(list); }
        catch (JsonProcessingException e) { return "[]"; }
    }
}
