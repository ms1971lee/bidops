package com.bidops.domain.analysis.worker;

import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.pipeline.OpenAiException;
import com.bidops.domain.analysis.repository.AnalysisJobRepository;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.service.ProjectActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 분석 Job 비동기 워커.
 *
 * 두 가지 방식으로 Job을 처리한다:
 * 1. 이벤트 수신 (@EventListener + @Async): Job 생성 즉시 비동기 실행
 * 2. DB 폴링 (@Scheduled): 누락된 PENDING Job을 주기적으로 수거
 *
 * 중복 실행 방지: tryStart()로 PENDING→RUNNING 원자적 전환.
 * 실패 재시도: canRetry() 확인 후 retry()로 PENDING 재설정.
 * 타임아웃: RUNNING 상태가 30분 이상이면 FAILED 처리.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisJobWorker {

    private final AnalysisJobRepository jobRepository;
    private final List<AnalysisJobHandler> handlers;
    private final ProjectActivityService activityService;

    private static final int TIMEOUT_MINUTES = 30;

    @PostConstruct
    void logHandlers() {
        log.info("[Worker] 등록된 분석 핸들러 {}건:", handlers.size());
        handlers.forEach(h -> log.info("[Worker]   - {}", h.getClass().getSimpleName()));
    }

    // ── 이벤트 수신: Job 생성 즉시 비동기 실행 ────────────────────────────

    @Async
    @EventListener
    public void onJobCreated(AnalysisJobEvent event) {
        log.info("[Worker] 이벤트 수신: jobId={}", event.jobId());
        processJob(event.jobId());
    }

    // ── DB 폴링: 누락된 PENDING Job 수거 (30초 간격) ───────────────────

    @Scheduled(fixedDelay = 30_000, initialDelay = 10_000)
    public void pollPendingJobs() {
        List<AnalysisJob> pending = jobRepository.findPendingJobs();
        if (!pending.isEmpty()) {
            log.info("[Worker] 폴링: PENDING Job {}건 발견", pending.size());
            pending.forEach(job -> processJob(job.getId()));
        }

        // 타임아웃 감지
        checkStuckJobs();
    }

    // ── Job 실행 핵심 로직 ─────────────────────────────────────────────

    private void processJob(String jobId) {
        try {
            // 1. Job 로드 + PENDING→RUNNING 전환 (중복 실행 방지)
            AnalysisJob job = claimJob(jobId);
            if (job == null) return; // 이미 다른 워커가 처리 중

            // 2. 핸들러 찾기
            AnalysisJobHandler handler = findHandler(job);

            // 3. 실행 (progress 콜백으로 진행률 DB 저장)
            try {
                AnalysisJobHandler.ProgressCallback callback = new AnalysisJobHandler.ProgressCallback() {
                    @Override public void report(int percent) { updateProgress(jobId, percent, null); }
                    @Override public void reportStep(int percent, String step) { updateProgress(jobId, percent, step); }
                };
                int resultCount = handler.execute(job, callback);
                completeJob(jobId, resultCount);
            } catch (Exception e) {
                log.error("[Worker] Job 실행 실패: jobId={}", jobId, e);
                handleFailure(jobId, e);
            }
        } catch (Exception e) {
            log.error("[Worker] Job 처리 중 예외: jobId={}", jobId, e);
        }
    }

    @Transactional
    protected AnalysisJob claimJob(String jobId) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("[Worker] Job 없음: jobId={}", jobId);
            return null;
        }
        if (!job.tryStart()) {
            log.info("[Worker] Job 이미 처리 중 (status={}): jobId={}", job.getStatus(), jobId);
            return null;
        }
        jobRepository.save(job);
        log.info("[Worker] Job 시작: jobId={} type={}", jobId, job.getJobType());
        return job;
    }

    private void updateProgress(String jobId, int percent, String step) {
        try {
            jobRepository.updateProgressById(jobId, Math.min(100, Math.max(0, percent)), step);
        } catch (Exception e) {
            log.warn("[Worker] progress 업데이트 실패 (무시): jobId={} percent={} step={}", jobId, percent, step);
        }
    }

    @Transactional
    protected void completeJob(String jobId, int resultCount) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;
        job.complete(resultCount);
        jobRepository.save(job);
        log.info("[Worker] Job 완료: jobId={} resultCount={}", jobId, resultCount);

        activityService.record(job.getProjectId(), ActivityType.ANALYSIS_COMPLETED,
                "분석 완료: " + job.getJobType() + " (" + resultCount + "건)",
                "system", jobId, "analysis_job", null);
    }

    @Transactional
    protected void handleFailure(String jobId, Exception e) {
        AnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        // OpenAiException이면 구조화된 에러 코드/메시지 사용
        String errorCode;
        String errorMessage;
        boolean retryable;

        if (e instanceof OpenAiException oae) {
            errorCode = oae.getErrorCode().name();
            errorMessage = oae.getMessage(); // 사용자용 짧은 문구
            retryable = oae.isRetryable();
            log.warn("[Worker] OpenAI 오류: jobId={} code={} httpStatus={} retryable={} detail={}",
                    jobId, errorCode, oae.getHttpStatus(), retryable, oae.getProviderDetail());
        } else {
            errorCode = e.getClass().getSimpleName();
            errorMessage = e.getMessage() != null ? e.getMessage() : "알 수 없는 오류";
            retryable = true; // 미분류 오류는 재시도 허용
        }
        if (errorMessage.length() > 450) errorMessage = errorMessage.substring(0, 450);

        int totalAttempts = job.getRetryCount() + 1; // 현재까지 총 시도 횟수
        int maxTotal = job.getMaxRetries() + 1;    // 최대 총 시도 횟수

        if (retryable && job.canRetry()) {
            job.retry();
            jobRepository.save(job);
            log.info("[Worker] Job 재시도 예약: jobId={} attempt={}/{} errorCode={}",
                    jobId, totalAttempts, maxTotal, errorCode);
        } else {
            job.fail(errorCode, errorMessage);
            jobRepository.save(job);
            log.warn("[Worker] Job 최종 실패: jobId={} attempt={}/{} errorCode={} retryable={} error={}",
                    jobId, totalAttempts, maxTotal, errorCode, retryable, errorMessage);

            activityService.record(job.getProjectId(), ActivityType.ANALYSIS_FAILED,
                    "분석 실패: " + job.getJobType() + " - " + errorMessage,
                    "system", jobId, "analysis_job", errorMessage);
        }
    }

    // ── 타임아웃 감지 ─────────────────────────────────────────────────

    @Transactional
    protected void checkStuckJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<AnalysisJob> stuck = jobRepository.findStuckRunningJobs(cutoff);
        for (AnalysisJob job : stuck) {
            log.warn("[Worker] 타임아웃 Job 감지: jobId={} startedAt={}", job.getId(), job.getStartedAt());
            if (job.canRetry()) {
                job.retry();
            } else {
                job.fail("TIMEOUT", "분석 작업이 " + TIMEOUT_MINUTES + "분 내에 완료되지 않았습니다.");
                activityService.record(job.getProjectId(), ActivityType.ANALYSIS_FAILED,
                        "분석 타임아웃: " + job.getJobType(),
                        "system", job.getId(), "analysis_job", "타임아웃");
            }
            jobRepository.save(job);
        }
    }

    private AnalysisJobHandler findHandler(AnalysisJob job) {
        return handlers.stream()
                .filter(h -> h.supports(job))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(
                        "지원되지 않는 Job 타입: " + job.getJobType()));
    }
}
