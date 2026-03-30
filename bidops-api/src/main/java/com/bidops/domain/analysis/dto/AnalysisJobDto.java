package com.bidops.domain.analysis.dto;

import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** openapi.yaml AnalysisJob schema */
@Getter
@Builder
public class AnalysisJobDto {

    private String id;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("target_requirement_id")
    private String targetRequirementId;

    @JsonProperty("job_type")
    private AnalysisJobType jobType;

    private AnalysisJobStatus status;
    private Integer progress;

    @JsonProperty("progress_step")
    private String progressStep;

    @JsonProperty("started_at")
    private LocalDateTime startedAt;

    @JsonProperty("finished_at")
    private LocalDateTime finishedAt;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("result_count")
    private Integer resultCount;

    @JsonProperty("retry_count")
    private Integer retryCount;

    @JsonProperty("max_retries")
    private Integer maxRetries;

    @JsonProperty("cache_hit")
    private boolean cacheHit;

    @JsonProperty("analysis_prompt_version")
    private String analysisPromptVersion;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static AnalysisJobDto from(AnalysisJob j) {
        return AnalysisJobDto.builder()
                .id(j.getId())
                .projectId(j.getProjectId())
                .documentId(j.getDocumentId())
                .targetRequirementId(j.getTargetRequirementId())
                .jobType(j.getJobType())
                .status(j.getStatus())
                .progress(j.getStatus() == AnalysisJobStatus.COMPLETED ? 100 : j.getProgress())
                .progressStep(j.getStatus() == AnalysisJobStatus.COMPLETED ? "DONE" : j.getProgressStep())
                .startedAt(j.getStartedAt())
                .finishedAt(j.getFinishedAt())
                .errorCode(j.getErrorCode())
                .errorMessage(j.getErrorMessage())
                .resultCount(j.getResultCount())
                .retryCount(j.getRetryCount())
                .maxRetries(j.getMaxRetries())
                .cacheHit(j.isCacheHit())
                .analysisPromptVersion(j.getAnalysisPromptVersion())
                .createdAt(j.getCreatedAt())
                .build();
    }
}
