package com.bidops.domain.analysis.worker;

import com.bidops.domain.analysis.entity.AnalysisJob;

/**
 * 분석 Job 실행 핸들러 인터페이스.
 * 각 AnalysisJobType별로 구현체를 제공하거나,
 * 단일 핸들러에서 jobType으로 분기.
 */
public interface AnalysisJobHandler {

    /**
     * Job 실행. 성공 시 결과 수 반환.
     * 실패 시 RuntimeException throw → 워커가 fail 처리.
     */
    int execute(AnalysisJob job);

    /**
     * Job 실행 (progress 콜백 포함).
     * 기본 구현은 콜백 없이 execute(job) 호출.
     */
    default int execute(AnalysisJob job, ProgressCallback callback) {
        return execute(job);
    }

    boolean supports(AnalysisJob job);

    @FunctionalInterface
    interface ProgressCallback {
        void report(int percent);
    }
}
