package com.bidops.domain.analysis.entity;

import com.bidops.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

/**
 * 분석 이슈 (QG 실패, 추출 누락, 저장 실패 등).
 * QG 실패 항목도 requirement_id nullable로 저장하여 누락 추적 가능.
 */
@Entity
@Table(name = "analysis_issues", indexes = {
        @Index(name = "idx_ai_project", columnList = "project_id"),
        @Index(name = "idx_ai_job", columnList = "analysis_job_id"),
        @Index(name = "idx_ai_requirement", columnList = "requirement_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AnalysisIssue extends BaseEntity {

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

    /** nullable: 저장 전 실패 시 null, 저장 후 실패 시 연결 */
    @Column(name = "requirement_id", length = 36)
    private String requirementId;

    @Column(name = "original_req_no", length = 50)
    private String originalReqNo;

    @Column(name = "clause_id", length = 50)
    private String clauseId;

    @Column(name = "page_no")
    private Integer pageNo;

    @Column(name = "source_excerpt", columnDefinition = "TEXT")
    private String sourceExcerpt;

    /** QG_FAIL / EXTRACTION_MISS / SAVE_FAIL / GENERIC_CONTENT */
    @Column(name = "issue_type", nullable = false, length = 30)
    private String issueType;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /** 실패한 AI 출력 원본 (재생성 참고용) */
    @Column(name = "raw_ai_output", columnDefinition = "TEXT")
    private String rawAiOutput;

    /** OPEN / RESOLVED / IGNORED */
    @Column(name = "resolution_status", length = 20)
    @Builder.Default
    private String resolutionStatus = "OPEN";
}
