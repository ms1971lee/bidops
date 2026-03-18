package com.bidops.domain.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * AI 워커가 분석 결과를 일괄 전달할 때 사용하는 wrapper DTO.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfpAnalysisResultRequest {

    @NotNull(message = "analysis_job_id는 필수입니다")
    @JsonProperty("analysis_job_id")
    private String analysisJobId;

    @NotNull(message = "document_id는 필수입니다")
    @JsonProperty("document_id")
    private String documentId;

    @Valid
    @NotEmpty(message = "results는 1건 이상이어야 합니다")
    private List<RfpAnalysisResultItem> results;
}
