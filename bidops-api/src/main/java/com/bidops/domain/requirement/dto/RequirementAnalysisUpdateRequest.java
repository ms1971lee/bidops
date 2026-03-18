package com.bidops.domain.requirement.dto;

import com.bidops.domain.requirement.enums.FactLevel;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * PATCH /requirements/{requirementId}/analysis
 * operationId: updateRequirementAnalysis
 *
 * RequirementInsight 9개 필드만 수정 가능.
 * review_status 변경은 /review-status 엔드포인트를 사용할 것.
 */
@Getter
@NoArgsConstructor
public class RequirementAnalysisUpdateRequest {

    /** 확정 정보 요약 (원문 기반) */
    @JsonProperty("fact_summary")
    private String factSummary;

    /** AI 해석 요약 */
    @JsonProperty("interpretation_summary")
    private String interpretationSummary;

    /** 발주처 의도 */
    @JsonProperty("intent_summary")
    private String intentSummary;

    /** 제안서 반영 포인트 */
    @JsonProperty("proposal_point")
    private String proposalPoint;

    /** 구현 방향 */
    @JsonProperty("implementation_approach")
    private String implementationApproach;

    /** 필요 산출물 목록 */
    @JsonProperty("expected_deliverables")
    private List<String> expectedDeliverables;

    /** 차별화 포인트 */
    @JsonProperty("differentiation_point")
    private String differentiationPoint;

    /** 리스크/주의사항 목록 */
    @JsonProperty("risk_note")
    private List<String> riskNote;

    /** 질의 필요 여부 */
    @JsonProperty("query_needed")
    private Boolean queryNeeded;

    /**
     * 사실/추론/검토필요 구분.
     * AI가 FACT로 초기 설정하더라도 실무자가 직접 변경해야 확정.
     */
    @JsonProperty("fact_level")
    private FactLevel factLevel;
}
