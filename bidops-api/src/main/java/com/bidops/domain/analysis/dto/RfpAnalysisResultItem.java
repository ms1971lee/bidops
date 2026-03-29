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

    // ── 심화 분석 필드 ────────────────────────────────────────────

    @JsonProperty("proposal_point")
    private String proposalPoint;

    @JsonProperty("implementation_direction")
    private String implementationDirection;

    @JsonProperty("deliverables")
    private String deliverables;

    @JsonProperty("differentiation")
    private String differentiation;

    @JsonProperty("risk")
    private String risk;

    @JsonProperty("query_needed")
    private Boolean queryNeeded;

    @JsonProperty("mandatory")
    private Boolean mandatory;

    // ── 커버리지 매핑 ──────────────────────────────────────────

    /** 원문 요구사항 번호 (쉼표 구분, e.g. "MAR-001, MAR-002") */
    @JsonProperty("original_requirement_nos")
    private String originalRequirementNos;

    /** 추출 상태: SINGLE / MERGED / MISSING_CANDIDATE */
    @JsonProperty("extraction_status")
    private String extractionStatus;

    /** 병합 사유 (MERGED일 때) */
    @JsonProperty("merge_reason")
    private String mergeReason;

    // ── 심화 분석 추가 필드 ────────────────────────────────────

    /** 발주처 의도 해석 */
    @JsonProperty("interpretation")
    private String interpretation;

    /** 평가 시 중점 확인 사항 */
    @JsonProperty("evaluation_focus")
    private String evaluationFocus;

    /** 필요 증빙/근거 자료 */
    @JsonProperty("required_evidence")
    private String requiredEvidence;

    /** 제안서 초안 스니펫 (바로 복사 가능한 수준) */
    @JsonProperty("draft_proposal_snippet")
    private String draftProposalSnippet;

    /** 발주처 질의 필요 사항 */
    @JsonProperty("clarification_questions")
    private String clarificationQuestions;
}
