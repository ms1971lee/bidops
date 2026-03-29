package com.bidops.domain.analysis.entity;

import com.bidops.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

/**
 * 품질 게이트 검증 결과.
 * 각 Requirement에 대한 QG 검증 결과를 저장한다.
 */
@Entity
@Table(name = "quality_gate_results", indexes = {
        @Index(name = "idx_qgr_requirement", columnList = "requirement_id"),
        @Index(name = "idx_qgr_job", columnList = "analysis_job_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class QualityGateResult extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "requirement_id", nullable = false, length = 36)
    private String requirementId;

    @Column(name = "analysis_job_id", length = 36)
    private String analysisJobId;

    /** PENDING / PASS / WARN / FAIL */
    @Column(name = "gate_status", nullable = false, length = 20)
    @Builder.Default
    private String gateStatus = "PENDING";

    @Column(name = "total_checks")
    @Builder.Default
    private int totalChecks = 0;

    @Column(name = "passed_checks")
    @Builder.Default
    private int passedChecks = 0;

    @Column(name = "failed_checks")
    @Builder.Default
    private int failedChecks = 0;

    /** JSON array: ["원문 재진술", "generic 문장"] */
    @Column(name = "failure_reasons", columnDefinition = "TEXT")
    private String failureReasons;

    /** JSON array: [{rule, status, message}] */
    @Column(name = "check_details", columnDefinition = "TEXT")
    private String checkDetails;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;
}
