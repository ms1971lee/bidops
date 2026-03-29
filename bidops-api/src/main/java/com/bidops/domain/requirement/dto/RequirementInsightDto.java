package com.bidops.domain.requirement.dto;

import com.bidops.domain.requirement.entity.RequirementInsight;
import com.bidops.domain.requirement.enums.FactLevel;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * AI 분석 전용 응답 DTO.
 * 검토 상태(review_status, reviewed_by 등)는 RequirementReviewDto 에서 별도 제공.
 */
@Slf4j
@Getter
@Builder
public class RequirementInsightDto {

    @JsonProperty("requirement_id")
    private String requirementId;

    @JsonProperty("fact_summary")
    private String factSummary;

    @JsonProperty("interpretation_summary")
    private String interpretationSummary;

    @JsonProperty("intent_summary")
    private String intentSummary;

    @JsonProperty("proposal_point")
    private String proposalPoint;

    @JsonProperty("implementation_approach")
    private String implementationApproach;

    @JsonProperty("expected_deliverables")
    private List<String> expectedDeliverables;

    @JsonProperty("differentiation_point")
    private String differentiationPoint;

    @JsonProperty("risk_note")
    private List<String> riskNote;

    @JsonProperty("query_needed")
    private boolean queryNeeded;

    @JsonProperty("fact_level")
    private FactLevel factLevel;

    @JsonProperty("generated_by_job_id")
    private String generatedByJobId;

    @JsonProperty("evaluation_focus")
    private String evaluationFocus;

    @JsonProperty("required_evidence")
    private String requiredEvidence;

    @JsonProperty("draft_proposal_snippet")
    private String draftProposalSnippet;

    @JsonProperty("clarification_questions")
    private String clarificationQuestions;

    // ── factory ──────────────────────────────────────────────────────────────
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static RequirementInsightDto from(RequirementInsight i) {
        return RequirementInsightDto.builder()
                .requirementId(i.getRequirementId())
                .factSummary(i.getFactSummary())
                .interpretationSummary(i.getInterpretationSummary())
                .intentSummary(i.getIntentSummary())
                .proposalPoint(i.getProposalPoint())
                .implementationApproach(i.getImplementationApproach())
                .expectedDeliverables(parseList(i.getExpectedDeliverablesJson()))
                .differentiationPoint(i.getDifferentiationPoint())
                .riskNote(parseList(i.getRiskNoteJson()))
                .queryNeeded(i.isQueryNeeded())
                .factLevel(i.getFactLevel())
                .generatedByJobId(i.getGeneratedByJobId())
                .evaluationFocus(i.getEvaluationFocus())
                .requiredEvidence(i.getRequiredEvidence())
                .draftProposalSnippet(i.getDraftProposalSnippet())
                .clarificationQuestions(i.getClarificationQuestions())
                .build();
    }

    public static RequirementInsightDto empty(String requirementId) {
        return RequirementInsightDto.builder()
                .requirementId(requirementId)
                .factLevel(FactLevel.REVIEW_NEEDED)
                .expectedDeliverables(Collections.emptyList())
                .riskNote(Collections.emptyList())
                .build();
    }

    private static List<String> parseList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try { return MAPPER.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { log.warn("JSON 파싱 실패: {}", json); return Collections.emptyList(); }
    }
}
