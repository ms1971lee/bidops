package com.bidops.domain.requirement.entity;

import com.bidops.common.entity.BaseEntity;
import com.bidops.domain.requirement.enums.FactLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

/**
 * AI가 생성하고 실무자가 수정 가능한 분석 레이어.
 * 검토/승인 상태(RequirementReview)와 완전히 분리.
 *
 * RequirementInsight → AI 분석 내용 (what / how)
 * RequirementReview  → 사람 검토 결과 (approve / hold / comment)
 */
@Entity
@Table(name = "requirement_insights",
       indexes = @Index(name = "idx_req_insights_req", columnList = "requirement_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class RequirementInsight extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "requirement_id", nullable = false, unique = true, length = 36)
    private String requirementId;

    @Column(name = "fact_summary", columnDefinition = "TEXT")
    private String factSummary;

    @Column(name = "interpretation_summary", columnDefinition = "TEXT")
    private String interpretationSummary;

    @Column(name = "intent_summary", columnDefinition = "TEXT")
    private String intentSummary;

    @Column(name = "proposal_point", columnDefinition = "TEXT")
    private String proposalPoint;

    @Column(name = "implementation_approach", columnDefinition = "TEXT")
    private String implementationApproach;

    /** JSON array: ["산출물A","산출물B"] */
    @Column(name = "expected_deliverables", columnDefinition = "TEXT")
    private String expectedDeliverablesJson;

    @Column(name = "differentiation_point", columnDefinition = "TEXT")
    private String differentiationPoint;

    /** JSON array: ["리스크A","리스크B"] */
    @Column(name = "risk_note", columnDefinition = "TEXT")
    private String riskNoteJson;

    @Column(name = "query_needed", nullable = false)
    @Builder.Default
    private boolean queryNeeded = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "fact_level", nullable = false, length = 20)
    @Builder.Default
    private FactLevel factLevel = FactLevel.REVIEW_NEEDED;

    @Column(name = "generated_by_job_id", length = 36)
    private String generatedByJobId;

    /** 평가 시 중점 확인 사항 */
    @Column(name = "evaluation_focus", columnDefinition = "TEXT")
    private String evaluationFocus;

    /** 필요 증빙/근거 자료 */
    @Column(name = "required_evidence", columnDefinition = "TEXT")
    private String requiredEvidence;

    /** 제안서 초안 스니펫 */
    @Column(name = "draft_proposal_snippet", columnDefinition = "TEXT")
    private String draftProposalSnippet;

    /** 발주처 질의 필요 사항 */
    @Column(name = "clarification_questions", columnDefinition = "TEXT")
    private String clarificationQuestions;

    // ── 프롬프트 버전 추적 ──────────────────────────────────────────────────────

    @Column(name = "split_prompt_version", length = 50)
    private String splitPromptVersion;

    @Column(name = "analysis_prompt_version", length = 50)
    private String analysisPromptVersion;

    /** 재분석 입력 fingerprint (SHA-256 hex). originalText + sourceExcerpt + promptVersion 기반. */
    @Column(name = "input_fingerprint", length = 64)
    private String inputFingerprint;

    /** 품질 검증 이슈 목록 (JSON array). REVIEW_NEEDED 강등 사유 등. */
    @Column(name = "quality_issues", columnDefinition = "TEXT")
    private String qualityIssuesJson;

    public void update(String factSummary, String interpretationSummary,
                       String intentSummary, String proposalPoint,
                       String implementationApproach, String expectedDeliverablesJson,
                       String differentiationPoint, String riskNoteJson,
                       Boolean queryNeeded, FactLevel factLevel) {
        if (factSummary != null)              this.factSummary = factSummary;
        if (interpretationSummary != null)    this.interpretationSummary = interpretationSummary;
        if (intentSummary != null)            this.intentSummary = intentSummary;
        if (proposalPoint != null)            this.proposalPoint = proposalPoint;
        if (implementationApproach != null)   this.implementationApproach = implementationApproach;
        if (expectedDeliverablesJson != null) this.expectedDeliverablesJson = expectedDeliverablesJson;
        if (differentiationPoint != null)     this.differentiationPoint = differentiationPoint;
        if (riskNoteJson != null)             this.riskNoteJson = riskNoteJson;
        if (queryNeeded != null)              this.queryNeeded = queryNeeded;
        if (factLevel != null)                this.factLevel = factLevel;
    }

    /**
     * 단건 재분석 결과로 insight 전체 필드를 덮어쓴다.
     * generatedByJobId, analysisPromptVersion 갱신 포함.
     */
    public void overwriteForReanalysis(String factSummary, String interpretationSummary,
                                        String intentSummary, String proposalPoint,
                                        String implementationApproach, String expectedDeliverablesJson,
                                        String differentiationPoint, String riskNoteJson,
                                        boolean queryNeeded, FactLevel factLevel,
                                        String evaluationFocus, String requiredEvidence,
                                        String draftProposalSnippet, String clarificationQuestions,
                                        String generatedByJobId, String analysisPromptVersion,
                                        String inputFingerprint, String qualityIssuesJson) {
        this.factSummary = factSummary;
        this.interpretationSummary = interpretationSummary;
        this.intentSummary = intentSummary;
        this.proposalPoint = proposalPoint;
        this.implementationApproach = implementationApproach;
        this.expectedDeliverablesJson = expectedDeliverablesJson;
        this.differentiationPoint = differentiationPoint;
        this.riskNoteJson = riskNoteJson;
        this.queryNeeded = queryNeeded;
        this.factLevel = factLevel;
        this.evaluationFocus = evaluationFocus;
        this.requiredEvidence = requiredEvidence;
        this.draftProposalSnippet = draftProposalSnippet;
        this.clarificationQuestions = clarificationQuestions;
        this.generatedByJobId = generatedByJobId;
        this.analysisPromptVersion = analysisPromptVersion;
        this.inputFingerprint = inputFingerprint;
        this.qualityIssuesJson = qualityIssuesJson;
    }
}
