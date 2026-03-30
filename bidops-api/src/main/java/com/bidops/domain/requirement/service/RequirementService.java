package com.bidops.domain.requirement.service;

import com.bidops.common.response.ListData;
import com.bidops.domain.analysis.dto.AnalysisJobDto;
import com.bidops.domain.requirement.dto.*;
import com.bidops.domain.requirement.enums.*;

import java.util.List;

public interface RequirementService {

    // ── 요구사항 목록/상세 ─────────────────────────────────────────────────────
    ListData<RequirementDto> listRequirements(
            String projectId,
            RequirementCategory category, Boolean mandatory, Boolean evidenceRequired,
            RequirementAnalysisStatus analysisStatus, RequirementReviewStatus reviewStatus,
            FactLevel factLevel, Boolean queryNeeded, String keyword,
            String qualityIssueCode, String qualitySeverity,
            int page, int size);

    RequirementDetailDto getRequirement(String projectId, String requirementId);

    RequirementDetailDto updateRequirement(String projectId, String requirementId,
                                           RequirementUpdateRequest request);

    // ── 원문 근거 (SourceExcerpt 기반) ────────────────────────────────────────
    RequirementSourcesDto getSources(String projectId, String requirementId);

    // ── AI 분석 (RequirementInsight) ──────────────────────────────────────────
    RequirementInsightDto getInsight(String projectId, String requirementId);

    RequirementInsightDto updateInsight(String projectId, String requirementId,
                                        RequirementAnalysisUpdateRequest request);

    // ── 사람 검토 (RequirementReview) — Insight와 완전 분리 ───────────────────
    RequirementReviewDto getReview(String projectId, String requirementId);

    RequirementReviewDto changeReviewStatus(String projectId, String requirementId,
                                            RequirementReviewStatusChangeRequest request);

    // ── 단건 재분석 ─────────────────────────────────────────────────────────────
    RequirementReanalyzeResponseDto reanalyzeRequirementInsight(String projectId, String requirementId);

    // ── 일괄 재분석 ─────────────────────────────────────────────────────────────
    BatchReanalyzeResponseDto batchReanalyze(String projectId, List<String> requirementIds);

    BatchReanalyzeStatusDto getBatchReanalyzeStatus(String projectId, List<String> jobIds);

    // ── 재분석 이력 조회 ────────────────────────────────────────────────────────
    List<AnalysisJobDto> getReanalyzeHistory(String projectId, String requirementId, int limit);

    // ── 품질 이슈 통계 ─────────────────────────────────────────────────────────
    QualityStatsDto getQualityStats(String projectId);

    // ── 프로젝트 내 최근 재분석 상태 맵 ──────────────────────────────────────
    ReanalyzeStatusMapDto getReanalyzeStatusMap(String projectId);
}
