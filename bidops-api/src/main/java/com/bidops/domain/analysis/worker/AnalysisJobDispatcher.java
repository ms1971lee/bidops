package com.bidops.domain.analysis.worker;

import com.bidops.domain.analysis.entity.AnalysisJob;

/**
 * 분석 Job 디스패치 인터페이스.
 * 로컬: ApplicationEvent 발행 (PollingWorker가 처리)
 * 프로덕션: 외부 큐(SQS, RabbitMQ 등)로 교체 가능
 */
public interface AnalysisJobDispatcher {

    void dispatch(AnalysisJob job);
}
