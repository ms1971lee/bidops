package com.bidops.domain.requirement.dto;

import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * POST /requirements/{requirementId}/reanalyze 응답.
 *
 * requirementId + 분석 Job 상태 + insight(갱신됨) + review(변경 없이 그대로).
 */
@Getter
@Builder
public class RequirementReanalyzeResponseDto {

    @JsonProperty("requirement_id")
    private String requirementId;

    @JsonProperty("analysis_job_id")
    private String analysisJobId;

    @JsonProperty("analysis_status")
    private AnalysisJobStatus analysisStatus;

    private RequirementInsightDto insight;

    private RequirementReviewDto review;
}
