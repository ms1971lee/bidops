package com.bidops.domain.analysis.dto;

import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /projects/{projectId}/analysis-jobs */
@Getter
@NoArgsConstructor
public class AnalysisJobCreateRequest {

    @JsonProperty("document_id")
    @NotBlank(message = "document_id는 필수입니다.")
    private String documentId;

    @JsonProperty("job_type")
    @NotNull(message = "job_type은 필수입니다.")
    private AnalysisJobType jobType;
}
