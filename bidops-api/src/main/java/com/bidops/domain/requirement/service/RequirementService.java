package com.bidops.domain.requirement.service;

import com.bidops.common.response.ListData;
import com.bidops.domain.requirement.dto.*;
import com.bidops.domain.requirement.enums.*;

public interface RequirementService {

    // ── 요구사항 목록/상세 ─────────────────────────────────────────────────────
    ListData<RequirementDto> listRequirements(
            String projectId,
            RequirementCategory category, Boolean mandatory, Boolean evidenceRequired,
            RequirementAnalysisStatus analysisStatus, RequirementReviewStatus reviewStatus,
            FactLevel factLevel, Boolean queryNeeded, String keyword,
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
}
