package com.bidops.domain.analysis.dto;

import com.bidops.domain.analysis.enums.AnalysisResultStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * AI 워커 → 백엔드 전달용 RFP 분석 결과 단건 DTO.
 * docs/RFP_ANALYSIS_RESULT_SCHEMA.md 기준.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfpAnalysisResultItem {

    // ── 필수 필드 (4개) ──────────────────────────────────────────────

    @NotBlank(message = "requirement_text는 필수입니다")
    @JsonProperty("requirement_text")
    private String requirementText;

    @NotBlank(message = "requirement_type은 필수입니다")
    @JsonProperty("requirement_type")
    private String requirementType;

    @NotBlank(message = "original_evidence는 필수입니다")
    @JsonProperty("original_evidence")
    private String originalEvidence;

    @NotNull(message = "status는 필수입니다")
    private AnalysisResultStatus status;

    // ── 선택 필드 (6개) ──────────────────────────────────────────────

    @JsonProperty("clause_id")
    private String clauseId;

    @Positive(message = "page_no는 양수여야 합니다")
    @JsonProperty("page_no")
    private Integer pageNo;

    @JsonProperty("section_path")
    private String sectionPath;

    @JsonProperty("fact_basis")
    private String factBasis;

    @JsonProperty("inference_note")
    private String inferenceNote;

    @JsonProperty("review_required_note")
    private String reviewRequiredNote;
}
