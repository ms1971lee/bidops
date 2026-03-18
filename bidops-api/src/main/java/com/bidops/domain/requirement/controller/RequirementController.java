package com.bidops.domain.requirement.controller;

import com.bidops.common.response.ApiResponse;
import com.bidops.common.response.ListData;
import com.bidops.common.response.MetaDto;
import com.bidops.domain.requirement.dto.*;
import com.bidops.domain.requirement.enums.*;
import com.bidops.domain.requirement.service.RequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/requirements")
@RequiredArgsConstructor
@Tag(name = "Requirements")
public class RequirementController {

    private final RequirementService requirementService;

    // =========================================================================
    // tag: Requirements
    // =========================================================================

    /**
     * GET /projects/{projectId}/requirements
     * operationId: listRequirements
     */
    @GetMapping
    @Operation(summary = "요구사항 목록 조회", operationId = "listRequirements")
    public ApiResponse<ListData<RequirementDto>> listRequirements(
            @PathVariable String projectId,
            @RequestParam(required = false)                             RequirementCategory       category,
            @RequestParam(required = false)                             Boolean                   mandatory,
            @RequestParam(name = "evidence_required", required = false) Boolean                   evidenceRequired,
            @RequestParam(name = "analysis_status",   required = false) RequirementAnalysisStatus analysisStatus,
            @RequestParam(name = "review_status",     required = false) RequirementReviewStatus   reviewStatus,
            @RequestParam(name = "fact_level",        required = false) FactLevel                 factLevel,
            @RequestParam(name = "query_needed",      required = false) Boolean                   queryNeeded,
            @RequestParam(required = false)                             String                    keyword,
            @RequestParam(defaultValue = "1")                           int                       page,
            @RequestParam(defaultValue = "20")                          int                       size) {

        ListData<RequirementDto> data = requirementService.listRequirements(
                projectId, category, mandatory, evidenceRequired,
                analysisStatus, reviewStatus, factLevel, queryNeeded, keyword, page, size);
        return ApiResponse.ok(data, MetaDto.of(page, size, data.getTotalCount()));
    }

    /**
     * GET /projects/{projectId}/requirements/{requirementId}
     * operationId: getRequirement
     * data: { requirement, insight, review }
     */
    @GetMapping("/{requirementId}")
    @Operation(summary = "요구사항 상세 조회", operationId = "getRequirement")
    public ApiResponse<RequirementDetailDto> getRequirement(
            @PathVariable String projectId,
            @PathVariable String requirementId) {

        return ApiResponse.ok(requirementService.getRequirement(projectId, requirementId));
    }

    /**
     * PATCH /projects/{projectId}/requirements/{requirementId}
     * operationId: updateRequirement
     */
    @PatchMapping("/{requirementId}")
    @Operation(summary = "요구사항 기본 정보 수정", operationId = "updateRequirement")
    public ApiResponse<RequirementDetailDto> updateRequirement(
            @PathVariable String projectId,
            @PathVariable String requirementId,
            @RequestBody @Valid RequirementUpdateRequest request) {

        return ApiResponse.ok(requirementService.updateRequirement(projectId, requirementId, request));
    }

    /**
     * GET /projects/{projectId}/requirements/{requirementId}/sources
     * operationId: getRequirementSources
     * pageRefs, clauseRefs 는 SourceExcerpt 레코드에서 동적 조합하여 반환.
     */
    @GetMapping("/{requirementId}/sources")
    @Operation(summary = "요구사항 원문 근거 조회", operationId = "getRequirementSources")
    public ApiResponse<RequirementSourcesDto> getSources(
            @PathVariable String projectId,
            @PathVariable String requirementId) {

        return ApiResponse.ok(requirementService.getSources(projectId, requirementId));
    }

    // =========================================================================
    // tag: RequirementAnalysis — AI Insight 영역
    // =========================================================================

    /**
     * GET /projects/{projectId}/requirements/{requirementId}/analysis
     * operationId: getRequirementAnalysis
     * AI 분석 결과 조회 (fact_level, proposal_point 등).
     */
    @GetMapping("/{requirementId}/analysis")
    @Operation(summary = "요구사항 분석 조회 (AI Insight)",
               operationId = "getRequirementAnalysis",
               tags = "RequirementAnalysis")
    public ApiResponse<RequirementInsightDto> getInsight(
            @PathVariable String projectId,
            @PathVariable String requirementId) {

        return ApiResponse.ok(requirementService.getInsight(projectId, requirementId));
    }

    /**
     * PATCH /projects/{projectId}/requirements/{requirementId}/analysis
     * operationId: updateRequirementAnalysis
     * AI 분석 내용 수정 (실무자가 직접 보정).
     */
    @PatchMapping("/{requirementId}/analysis")
    @Operation(summary = "요구사항 분석 수정 (AI Insight)",
               operationId = "updateRequirementAnalysis",
               tags = "RequirementAnalysis")
    public ApiResponse<RequirementInsightDto> updateInsight(
            @PathVariable String projectId,
            @PathVariable String requirementId,
            @RequestBody @Valid RequirementAnalysisUpdateRequest request) {

        return ApiResponse.ok(requirementService.updateInsight(projectId, requirementId, request));
    }

    // =========================================================================
    // tag: RequirementAnalysis — 사람 Review 영역 (Insight와 분리)
    // =========================================================================

    /**
     * GET /projects/{projectId}/requirements/{requirementId}/review
     * 사람 검토 결과 조회 (review_status, comment, reviewer 등).
     */
    @GetMapping("/{requirementId}/review")
    @Operation(summary = "요구사항 검토 결과 조회",
               operationId = "getRequirementReview",
               tags = "RequirementAnalysis")
    public ApiResponse<RequirementReviewDto> getReview(
            @PathVariable String projectId,
            @PathVariable String requirementId) {

        return ApiResponse.ok(requirementService.getReview(projectId, requirementId));
    }

    /**
     * POST /projects/{projectId}/requirements/{requirementId}/review-status
     * operationId: changeRequirementReviewStatus
     * 검토 상태 변경 → RequirementReview 업데이트, Requirement.reviewStatus 동기화.
     */
    @PostMapping("/{requirementId}/review-status")
    @Operation(summary = "요구사항 검토 상태 변경",
               operationId = "changeRequirementReviewStatus",
               tags = "RequirementAnalysis")
    public ApiResponse<RequirementReviewDto> changeReviewStatus(
            @PathVariable String projectId,
            @PathVariable String requirementId,
            @RequestBody @Valid RequirementReviewStatusChangeRequest request) {

        return ApiResponse.ok(requirementService.changeReviewStatus(projectId, requirementId, request));
    }
}
