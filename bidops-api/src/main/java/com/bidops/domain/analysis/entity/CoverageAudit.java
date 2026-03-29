package com.bidops.domain.analysis.entity;

import com.bidops.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "coverage_audits", indexes = {
        @Index(name = "idx_coverage_project", columnList = "project_id"),
        @Index(name = "idx_coverage_document", columnList = "document_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class CoverageAudit extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    @Column(name = "analysis_job_id", length = 36)
    private String analysisJobId;

    @Column(name = "expected_count")
    private int expectedCount;

    @Column(name = "extracted_count")
    private int extractedCount;

    @Column(name = "saved_count")
    private int savedCount;

    @Column(name = "merged_count")
    private int mergedCount;

    @Column(name = "missing_count")
    private int missingCount;

    @Column(name = "coverage_rate")
    private float coverageRate;

    /** 누락된 원문 번호 목록 (JSON array) */
    @Column(name = "missing_req_nos", columnDefinition = "TEXT")
    private String missingReqNos;

    /** 카테고리별 기대/실제 비교 (JSON) */
    @Column(name = "category_summary", columnDefinition = "TEXT")
    private String categorySummary;

    /** 전체 매핑 상세 (JSON) */
    @Column(name = "audit_details", columnDefinition = "TEXT")
    private String auditDetails;

    // ── v2 상세 추적 필드 ──────────────────────────────────────────────────────

    @Column(name = "expected_original_nos", columnDefinition = "TEXT")
    private String expectedOriginalNos;

    @Column(name = "detected_original_nos", columnDefinition = "TEXT")
    private String detectedOriginalNos;

    @Column(name = "ai_extracted_original_nos", columnDefinition = "TEXT")
    private String aiExtractedOriginalNos;

    @Column(name = "saved_original_nos", columnDefinition = "TEXT")
    private String savedOriginalNos;

    @Column(name = "save_failed_nos", columnDefinition = "TEXT")
    private String saveFailedNos;

    @Column(name = "qg_failed_nos", columnDefinition = "TEXT")
    private String qgFailedNos;

    @Column(name = "visible_count")
    private Integer visibleCount;

    @Column(name = "visible_original_nos", columnDefinition = "TEXT")
    private String visibleOriginalNos;

    @Column(name = "hidden_after_query_nos", columnDefinition = "TEXT")
    private String hiddenAfterQueryNos;

    @Column(name = "final_missing_nos", columnDefinition = "TEXT")
    private String finalMissingNos;

    @Column(name = "merged_original_nos", columnDefinition = "TEXT")
    private String mergedOriginalNos;

    @Column(name = "merged_out_nos", columnDefinition = "TEXT")
    private String mergedOutNos;

    @Column(name = "missing_after_detection", columnDefinition = "TEXT")
    private String missingAfterDetection;

    @Column(name = "missing_after_ai", columnDefinition = "TEXT")
    private String missingAfterAi;

    @Column(name = "merge_details", columnDefinition = "TEXT")
    private String mergeDetailsJson;
}
