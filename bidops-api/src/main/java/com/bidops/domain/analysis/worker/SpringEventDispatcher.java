package com.bidops.domain.analysis.worker;

import com.bidops.domain.analysis.entity.AnalysisJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring ApplicationEvent 기반 디스패처.
 * 트랜잭션 커밋 후 이벤트 발행 → PollingWorker가 비동기로 수신.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringEventDispatcher implements AnalysisJobDispatcher {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void dispatch(AnalysisJob job) {
        log.info("[Dispatcher] Job 디스패치: jobId={} type={}", job.getId(), job.getJobType());
        eventPublisher.publishEvent(new AnalysisJobEvent(
                job.getId(),
                job.getProjectId(),
                job.getDocumentId(),
                job.getJobType().name()
        ));
    }
}
