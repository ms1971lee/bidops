package com.bidops.domain.requirement.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.response.ListData;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.project.repository.ProjectRepository;
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
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final ProjectRepository            projectRepository;
    private final ObjectMapper                 objectMapper;

    // ── 목록 ─────────────────────────────────────────────────────────────────
    @Override
    public ListData<RequirementDto> listRequirements(
            String projectId,
            RequirementCategory category, Boolean mandatory, Boolean evidenceRequired,
            RequirementAnalysisStatus analysisStatus, RequirementReviewStatus reviewStatus,
            FactLevel factLevel, Boolean queryNeeded, String keyword,
            int page, int size) {

        validateProject(projectId);
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
        Requirement req = findOrThrow(projectId, requirementId);
        req.update(request.getTitle(), request.getCategory(),
                   request.getMandatoryFlag(), request.getEvidenceRequiredFlag(),
                   request.getAnalysisStatus());
        return getRequirement(projectId, requirementId);
    }

    // ── 원문 근거 (SourceExcerpt 기반 동적 조합) ──────────────────────────────
    @Override
    public RequirementSourcesDto getSources(String projectId, String requirementId) {
        findOrThrow(projectId, requirementId);

        // Step1: requirement → sourceExcerptId 목록 (requirement 도메인 내 처리)
        List<String> excerptIds = sourceRepository
                .findByRequirementIdOrderByLinkTypeAsc(requirementId)
                .stream()
                .map(rs -> rs.getSourceExcerptId())
                .distinct()
                .toList();

        if (excerptIds.isEmpty()) {
            return RequirementSourcesDto.from(java.util.Collections.emptyList());
        }

        // Step2: SourceExcerpt 일괄 조회 (document 도메인, 크로스 JPQL 없음)
        List<SourceExcerpt> excerpts = excerptRepository.findAllByIdInOrdered(excerptIds);
        return RequirementSourcesDto.from(excerpts);
    }

    // ── AI 분석 조회 ─────────────────────────────────────────────────────────
    @Override
    public RequirementInsightDto getInsight(String projectId, String requirementId) {
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

        return RequirementInsightDto.from(insightRepository.save(insight));
    }

    // ── 사람 검토 조회 ───────────────────────────────────────────────────────
    @Override
    public RequirementReviewDto getReview(String projectId, String requirementId) {
        findOrThrow(projectId, requirementId);
        return reviewRepository.findByRequirementId(requirementId)
                .map(RequirementReviewDto::from)
                .orElse(RequirementReviewDto.empty(requirementId));
    }

    // ── 검토 상태 변경 ────────────────────────────────────────────────────────
    @Override
    @Transactional
    public RequirementReviewDto changeReviewStatus(String projectId, String requirementId,
                                                   RequirementReviewStatusChangeRequest request) {
        Requirement req = findOrThrow(projectId, requirementId);

        RequirementReview review = reviewRepository.findByRequirementId(requirementId)
                .orElseGet(() -> RequirementReview.builder()
                        .requirementId(requirementId)
                        .build());

        review.changeStatus(request.getReviewStatus(), request.getReviewComment(), currentUserId());
        RequirementReview saved = reviewRepository.save(review);

        // Requirement 테이블에 비정규화 동기화 (목록 필터 성능용)
        req.syncReviewStatus(request.getReviewStatus());

        return RequirementReviewDto.from(saved);
    }

    // ── internal helpers ─────────────────────────────────────────────────────
    private Requirement findOrThrow(String projectId, String requirementId) {
        return requirementRepository.findByIdAndProjectId(requirementId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("요구사항"));
    }

    private void validateProject(String projectId) {
        projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> BidOpsException.notFound("프로젝트"));
    }

    private String currentUserId() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "system";
        }
    }

    private String toJson(java.util.List<String> list) {
        if (list == null) return null;
        try { return objectMapper.writeValueAsString(list); }
        catch (JsonProcessingException e) { return "[]"; }
    }
}
