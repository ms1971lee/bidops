package com.bidops.domain.requirement.entity;

import com.bidops.common.entity.BaseEntity;
import com.bidops.domain.requirement.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

/**
 * 요구사항 원문 및 분류 정보.
 *
 * 원문 근거(pageRefs, clauseRefs)는 이 엔티티에 저장하지 않는다.
 * → RequirementSource → SourceExcerpt 경로를 통해 동적으로 조합.
 *   (RequirementSourcesDto 에서만 pageRefs/clauseRefs 를 내려준다.)
 */
@Entity
@Table(name = "requirements", indexes = {
        @Index(name = "idx_requirements_project",  columnList = "project_id"),
        @Index(name = "idx_requirements_document", columnList = "document_id"),
        @Index(name = "idx_requirements_code",     columnList = "project_id, requirement_code")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Requirement extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "project_id",  nullable = false, length = 36)
    private String projectId;

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    /** 요구사항 고유 코드 (예: SFR-001, TER-003) */
    @Column(name = "requirement_code", nullable = false, length = 30)
    private String requirementCode;

    @Column(length = 200)
    private String title;

    @Column(name = "original_text", nullable = false, columnDefinition = "TEXT")
    private String originalText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RequirementCategory category;

    @Column(name = "mandatory_flag", nullable = false)
    @Builder.Default
    private boolean mandatoryFlag = false;

    @Column(name = "evidence_required_flag", nullable = false)
    @Builder.Default
    private boolean evidenceRequiredFlag = false;

    /** AI 추출 신뢰도 (0.0 ~ 1.0) */
    @Column(name = "confidence_score")
    private Float confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 20)
    @Builder.Default
    private RequirementAnalysisStatus analysisStatus = RequirementAnalysisStatus.EXTRACTED;

    /**
     * 검토 상태는 RequirementReview 엔티티에서 관리하는 것이 원칙이나,
     * 목록 조회 필터 성능을 위해 여기에도 비정규화하여 동기화한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", length = 20)
    @Builder.Default
    private RequirementReviewStatus reviewStatus = RequirementReviewStatus.NOT_REVIEWED;

    @Enumerated(EnumType.STRING)
    @Column(name = "fact_level", length = 20)
    @Builder.Default
    private FactLevel factLevel = FactLevel.REVIEW_NEEDED;

    @Column(name = "query_needed", nullable = false)
    @Builder.Default
    private boolean queryNeeded = false;

    // ── 추출 커버리지 필드 ──────────────────────────────────────────────────────

    /** 원문 요구사항 번호 목록 (JSON array, e.g. ["MAR-001","MAR-002"]) */
    @Column(name = "original_req_nos", columnDefinition = "TEXT")
    private String originalReqNos;

    /** 추출 상태: SINGLE(1:1), MERGED(N:1 병합), MISSING_CANDIDATE(누락의심) */
    @Column(name = "extraction_status", length = 30)
    @Builder.Default
    private String extractionStatus = "SINGLE";

    /** 병합 사유 */
    @Column(name = "merge_reason", columnDefinition = "TEXT")
    private String mergeReason;

    // ── 분석 엔진 v2 상태축 ────────────────────────────────────────────────────

    /** 원문 조항 참조 (SourceExcerpt.anchorLabel 조회 편의용, FK 아님) */
    @Column(name = "source_clause_id", length = 50)
    private String sourceClauseId;

    /** atomic requirement 여부 */
    @Column(name = "atomic_flag")
    @Builder.Default
    private boolean atomicFlag = true;

    /** 추출 상태 (DETECTED → SPLIT → EXTRACTION_FAILED) */
    @Column(name = "extraction_status_v2", length = 20)
    @Builder.Default
    private String extractionStatusV2 = "DETECTED";

    /** 심화 분석 상태 (PENDING → ENRICHED → ENRICHMENT_FAILED → SKIPPED) */
    @Setter
    @Column(name = "enrichment_status", length = 20)
    @Builder.Default
    private String enrichmentStatus = "PENDING";

    /** 품질 게이트 상태 (PENDING → PASS → WARN → FAIL) */
    @Setter
    @Column(name = "quality_gate_status", length = 20)
    @Builder.Default
    private String qualityGateStatus = "PENDING";

    /** 분석 Job 버전 (재분석 시 증가) */
    @Column(name = "analysis_job_version")
    @Builder.Default
    private int analysisJobVersion = 1;

    /** 아카이브 여부 (재분석 시 이전 결과 보존) */
    @Setter
    @Column(name = "archived")
    @Builder.Default
    private boolean archived = false;

    /** 목록 표시 여부 (QG 실패 시 false) */
    @Setter
    @Column(name = "visible")
    @Builder.Default
    private boolean visible = true;

    // ── 변경 메서드 ────────────────────────────────────────────────────────────
    public void update(String title, RequirementCategory category,
                       Boolean mandatoryFlag, Boolean evidenceRequiredFlag,
                       RequirementAnalysisStatus analysisStatus) {
        if (title != null)              this.title = title;
        if (category != null)           this.category = category;
        if (mandatoryFlag != null)      this.mandatoryFlag = mandatoryFlag;
        if (evidenceRequiredFlag != null) this.evidenceRequiredFlag = evidenceRequiredFlag;
        if (analysisStatus != null)     this.analysisStatus = analysisStatus;
    }

    /** RequirementReview 상태와 동기화 (비정규화) */
    public void syncReviewStatus(RequirementReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }
}
