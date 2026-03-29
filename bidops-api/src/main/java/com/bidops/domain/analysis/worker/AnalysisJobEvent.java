package com.bidops.domain.analysis.worker;

/**
 * Job 디스패치 시 발행되는 이벤트.
 * Spring ApplicationEventPublisher 기반.
 */
public record AnalysisJobEvent(
        String jobId,
        String projectId,
        String documentId,
        String jobType
) {}
