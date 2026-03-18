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

    @JsonProperty("job_type")
    private AnalysisJobType jobType;

    private AnalysisJobStatus status;
    private Integer progress;

    @JsonProperty("started_at")
    private LocalDateTime startedAt;

    @JsonProperty("finished_at")
    private LocalDateTime finishedAt;

    @JsonProperty("error_code")
    private String errorCode;

    @JsonProperty("error_message")
    private String errorMessage;

    public static AnalysisJobDto from(AnalysisJob j) {
        return AnalysisJobDto.builder()
                .id(j.getId())
                .projectId(j.getProjectId())
                .documentId(j.getDocumentId())
                .jobType(j.getJobType())
                .status(j.getStatus())
                .progress(j.getProgress())
                .startedAt(j.getStartedAt())
                .finishedAt(j.getFinishedAt())
                .errorCode(j.getErrorCode())
                .errorMessage(j.getErrorMessage())
                .build();
    }
}
