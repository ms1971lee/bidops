package com.bidops.domain.analysis.entity;

import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class AnalysisJob {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "document_id", nullable = false, length = 36)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private AnalysisJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AnalysisJobStatus status = AnalysisJobStatus.PENDING;

    /** 0~100 진행률 */
    @Column(nullable = false)
    @Builder.Default
    private Integer progress = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** 저장된 요구사항 수 */
    @Column(name = "result_count")
    @Builder.Default
    private Integer resultCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── 상태 변경 메서드 ────────────────────────────────────────────────────────
    public void start() {
        this.status    = AnalysisJobStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    public void complete(int resultCount) {
        this.status      = AnalysisJobStatus.COMPLETED;
        this.progress    = 100;
        this.resultCount = resultCount;
        this.finishedAt  = LocalDateTime.now();
    }

    public void fail(String errorCode, String errorMessage) {
        this.status       = AnalysisJobStatus.FAILED;
        this.finishedAt   = LocalDateTime.now();
        this.errorCode    = errorCode;
        this.errorMessage = errorMessage;
    }

    public void updateProgress(int progress) {
        this.progress = Math.min(100, Math.max(0, progress));
    }
}
