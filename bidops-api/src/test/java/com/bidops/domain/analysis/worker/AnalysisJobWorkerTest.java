package com.bidops.domain.analysis.worker;

import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.bidops.domain.analysis.repository.AnalysisJobRepository;
import com.bidops.domain.project.service.ProjectActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisJobWorkerTest {

    @Mock AnalysisJobRepository jobRepository;
    @Mock ProjectActivityService activityService;

    AnalysisJobWorker worker;

    // 성공하는 핸들러
    AnalysisJobHandler successHandler = new AnalysisJobHandler() {
        @Override public int execute(AnalysisJob job) { return 5; }
        @Override public boolean supports(AnalysisJob job) { return true; }
    };

    // 실패하는 핸들러
    AnalysisJobHandler failHandler = new AnalysisJobHandler() {
        @Override public int execute(AnalysisJob job) { throw new RuntimeException("AI API 오류"); }
        @Override public boolean supports(AnalysisJob job) { return true; }
    };

    static final String JOB_ID = "job-1";
    static final String PROJECT_ID = "proj-1";

    private AnalysisJob buildJob(AnalysisJobStatus status) {
        return AnalysisJob.builder()
                .id(JOB_ID).projectId(PROJECT_ID).documentId("doc-1")
                .jobType(AnalysisJobType.RFP_PARSE).status(status)
                .build();
    }

    @Nested
    @DisplayName("Job 실행 성공")
    class SuccessCase {
        @BeforeEach
        void setUp() {
            worker = new AnalysisJobWorker(jobRepository, List.of(successHandler), activityService);
        }

        @Test
        @DisplayName("PENDING Job → RUNNING → COMPLETED")
        void pendingJobCompletes() {
            AnalysisJob job = buildJob(AnalysisJobStatus.PENDING);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));

            worker.onJobCreated(new AnalysisJobEvent(JOB_ID, PROJECT_ID, "doc-1", "RFP_PARSE"));

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
            assertThat(job.getResultCount()).isEqualTo(5);
            assertThat(job.getProgress()).isEqualTo(100);
            then(jobRepository).should(times(2)).save(job); // start + complete
        }
    }

    @Nested
    @DisplayName("중복 실행 방지")
    class DuplicatePrevention {
        @BeforeEach
        void setUp() {
            worker = new AnalysisJobWorker(jobRepository, List.of(successHandler), activityService);
        }

        @Test
        @DisplayName("RUNNING Job은 다시 실행하지 않음")
        void runningJobSkipped() {
            AnalysisJob job = buildJob(AnalysisJobStatus.RUNNING);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));

            worker.onJobCreated(new AnalysisJobEvent(JOB_ID, PROJECT_ID, "doc-1", "RFP_PARSE"));

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
            then(jobRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("COMPLETED Job은 다시 실행하지 않음")
        void completedJobSkipped() {
            AnalysisJob job = buildJob(AnalysisJobStatus.COMPLETED);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));

            worker.onJobCreated(new AnalysisJobEvent(JOB_ID, PROJECT_ID, "doc-1", "RFP_PARSE"));

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
            then(jobRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("실패 + 재시도")
    class FailureRetry {
        @BeforeEach
        void setUp() {
            worker = new AnalysisJobWorker(jobRepository, List.of(failHandler), activityService);
        }

        @Test
        @DisplayName("첫 실패 → PENDING으로 재설정 (자동 재시도)")
        void firstFailureRetries() {
            AnalysisJob job = buildJob(AnalysisJobStatus.PENDING);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));

            worker.onJobCreated(new AnalysisJobEvent(JOB_ID, PROJECT_ID, "doc-1", "RFP_PARSE"));

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
            assertThat(job.getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("최대 재시도 초과 → FAILED")
        void maxRetriesExceeded() {
            AnalysisJob job = buildJob(AnalysisJobStatus.PENDING);
            // 이미 3번 시도
            try {
                var f = AnalysisJob.class.getDeclaredField("retryCount");
                f.setAccessible(true); f.set(job, 3);
            } catch (Exception e) { throw new RuntimeException(e); }

            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));

            worker.onJobCreated(new AnalysisJobEvent(JOB_ID, PROJECT_ID, "doc-1", "RFP_PARSE"));

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
            assertThat(job.getErrorMessage()).contains("AI API 오류");
        }
    }

    @Nested
    @DisplayName("타임아웃 감지")
    class Timeout {
        @BeforeEach
        void setUp() {
            worker = new AnalysisJobWorker(jobRepository, List.of(successHandler), activityService);
        }

        @Test
        @DisplayName("30분 이상 RUNNING → 재시도 또는 FAILED 처리")
        void stuckJobDetected() {
            AnalysisJob stuckJob = buildJob(AnalysisJobStatus.RUNNING);
            try {
                var f = AnalysisJob.class.getDeclaredField("startedAt");
                f.setAccessible(true); f.set(stuckJob, LocalDateTime.now().minusMinutes(60));
            } catch (Exception e) { throw new RuntimeException(e); }

            given(jobRepository.findStuckRunningJobs(any())).willReturn(List.of(stuckJob));
            given(jobRepository.findPendingJobs()).willReturn(List.of());

            worker.pollPendingJobs();

            // retryCount=0이므로 PENDING으로 재설정
            assertThat(stuckJob.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
            assertThat(stuckJob.getRetryCount()).isEqualTo(1);
            then(jobRepository).should().save(stuckJob);
        }
    }
}
