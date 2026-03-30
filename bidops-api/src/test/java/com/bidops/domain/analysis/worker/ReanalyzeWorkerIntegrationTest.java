package com.bidops.domain.analysis.worker;

import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.bidops.domain.analysis.pipeline.OpenAiClient;
import com.bidops.domain.analysis.pipeline.OpenAiErrorCode;
import com.bidops.domain.analysis.pipeline.OpenAiException;
import com.bidops.domain.analysis.repository.AnalysisJobRepository;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.entity.RequirementInsight;
import com.bidops.domain.requirement.entity.RequirementReview;
import com.bidops.domain.requirement.entity.RequirementSource;
import com.bidops.domain.requirement.enums.FactLevel;
import com.bidops.domain.requirement.enums.RequirementCategory;
import com.bidops.domain.requirement.enums.RequirementReviewStatus;
import com.bidops.domain.requirement.repository.RequirementInsightRepository;
import com.bidops.domain.requirement.repository.RequirementRepository;
import com.bidops.domain.requirement.repository.RequirementReviewRepository;
import com.bidops.domain.requirement.repository.RequirementSourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * AnalysisJobWorker + RequirementInsightReanalyzeHandler 통합 테스트.
 *
 * 워커가 REQUIREMENT_INSIGHT_REANALYZE PENDING job을 폴링하여
 * 핸들러를 선택하고 실행 → COMPLETED/FAILED까지의 전체 흐름을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ReanalyzeWorkerIntegrationTest {

    @Mock AnalysisJobRepository jobRepository;
    @Mock ProjectActivityService activityService;
    @Mock RequirementRepository requirementRepository;
    @Mock RequirementInsightRepository insightRepository;
    @Mock RequirementReviewRepository reviewRepository;
    @Mock RequirementSourceRepository sourceRepository;
    @Mock SourceExcerptRepository excerptRepository;
    @Mock OpenAiClient openAiClient;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    AnalysisJobWorker worker;
    RequirementInsightReanalyzeHandler reanalyzeHandler;

    static final String JOB_ID = "job-reanalyze-1";
    static final String PROJECT_ID = "proj-1";
    static final String DOCUMENT_ID = "doc-1";
    static final String REQUIREMENT_ID = "req-1";

    private static final String MOCK_GPT_RESPONSE = """
            {
              "fact_summary": "p.5 조항 3.1.2에 실시간 모니터링 요구사항이 명시되어 있음",
              "interpretation_summary": "발주처는 운영 장애 조기 탐지를 위한 실시간 모니터링을 요구함. 평가위원은 모니터링 대시보드의 구체성을 확인할 것으로 예상됨. 과거 프로젝트에서 장애 대응 지연을 경험했을 가능성이 높음.",
              "intent_summary": "운영 장애 조기 탐지를 위한 실시간 모니터링 체계 구축",
              "proposal_point": "Prometheus+Grafana 기반 대시보드를 구축하여 4대 핵심 지표를 5초 주기로 수집하고, 임계치 초과 시 자동 알림 체계를 운영합니다.",
              "implementation_approach": "1)관리대상: 서버/네트워크/애플리케이션 2)수행주체: 운영팀 3)절차: 수집→분석→알림→대응 4)주기: 5초 5)도구: Prometheus+Grafana 6)산출물: 모니터링 운영계획서, 월간점검보고서 7)검증: 월간 KPI 점검",
              "expected_deliverables": ["모니터링 운영계획서", "장애 대응 매뉴얼"],
              "differentiation_point": "AIOps 기반 이상 징후 사전 예측으로 장애 발생 30분 전 자동 경고 체계를 구축하여 MTTR을 50% 단축",
              "risk_note": ["모니터링 대상 급증 시 수집 지연: 수집 주기 동적 조절 메커니즘 적용"],
              "evaluation_focus": "모니터링 대상 범위, 수집 주기, 알림 정책, 장애 대응 프로세스의 구체성",
              "required_evidence": "모니터링 대시보드 샘플 화면, 장애 대응 프로세스 흐름도, 유사 프로젝트 운영 실적",
              "draft_proposal_snippet": "본 사업에서는 Prometheus 기반 메트릭 수집과 Grafana 대시보드를 활용하여 실시간 모니터링 체계를 구축합니다. 서버 4대 핵심 지표를 5초 주기로 수집하며, 임계치 초과 시 Slack/SMS 알림을 자동 발송합니다. AIOps 모듈을 통해 장애 예방 효과를 극대화합니다.",
              "clarification_questions": null,
              "query_needed": false,
              "fact_level": "FACT"
            }
            """;

    @BeforeEach
    void setUp() {
        reanalyzeHandler = new RequirementInsightReanalyzeHandler(
                requirementRepository, insightRepository, sourceRepository,
                excerptRepository, openAiClient, objectMapper);
        // 워커에 재분석 핸들러만 등록
        worker = new AnalysisJobWorker(jobRepository, List.of(reanalyzeHandler), activityService);
    }

    private AnalysisJob buildReanalyzeJob(String targetRequirementId) {
        return AnalysisJob.builder()
                .id(JOB_ID)
                .projectId(PROJECT_ID)
                .documentId(DOCUMENT_ID)
                .targetRequirementId(targetRequirementId)
                .jobType(AnalysisJobType.REQUIREMENT_INSIGHT_REANALYZE)
                .status(AnalysisJobStatus.PENDING)
                .build();
    }

    private void givenRequirementAndSources() {
        Requirement req = Requirement.builder()
                .id(REQUIREMENT_ID)
                .projectId(PROJECT_ID)
                .documentId(DOCUMENT_ID)
                .requirementCode("SFR-001")
                .originalText("시스템은 실시간 모니터링을 제공해야 한다.")
                .category(RequirementCategory.FUNCTIONAL)
                .build();
        given(requirementRepository.findById(REQUIREMENT_ID)).willReturn(Optional.of(req));

        RequirementSource rs = RequirementSource.builder()
                .requirementId(REQUIREMENT_ID)
                .sourceExcerptId("excerpt-1")
                .linkType(RequirementSource.LinkType.PRIMARY)
                .build();
        given(sourceRepository.findByRequirementIdOrderByLinkTypeAsc(REQUIREMENT_ID))
                .willReturn(List.of(rs));

        SourceExcerpt excerpt = SourceExcerpt.builder()
                .id("excerpt-1")
                .documentId(DOCUMENT_ID)
                .pageNo(5)
                .excerptType(SourceExcerpt.ExcerptType.PARAGRAPH)
                .anchorLabel("3.1.2")
                .rawText("시스템은 실시간 모니터링을 제공해야 한다.")
                .build();
        given(excerptRepository.findAllByIdInOrdered(List.of("excerpt-1")))
                .willReturn(List.of(excerpt));
    }

    // ═══════════════════════════════════════════════════════════════════
    // 성공 케이스
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("정상 재분석 흐름")
    class SuccessFlow {

        @Test
        @DisplayName("PENDING → RUNNING → COMPLETED: insight overwrite, review 미변경")
        void fullSuccessFlow() {
            AnalysisJob job = buildReanalyzeJob(REQUIREMENT_ID);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));
            givenRequirementAndSources();
            given(openAiClient.chat(anyString(), anyString())).willReturn(MOCK_GPT_RESPONSE);
            given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
            given(insightRepository.save(any(RequirementInsight.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            // 워커 이벤트 트리거
            worker.onJobCreated(new AnalysisJobEvent(
                    JOB_ID, PROJECT_ID, DOCUMENT_ID, "REQUIREMENT_INSIGHT_REANALYZE"));

            // Job 최종 상태 검증
            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
            assertThat(job.getResultCount()).isEqualTo(1);
            assertThat(job.getProgress()).isEqualTo(100);
            assertThat(job.getFinishedAt()).isNotNull();

            // Insight 저장 검증
            ArgumentCaptor<RequirementInsight> insightCaptor = ArgumentCaptor.forClass(RequirementInsight.class);
            then(insightRepository).should().save(insightCaptor.capture());
            RequirementInsight saved = insightCaptor.getValue();
            assertThat(saved.getFactSummary()).contains("p.5");
            assertThat(saved.getFactLevel()).isEqualTo(FactLevel.FACT);
            assertThat(saved.getGeneratedByJobId()).isEqualTo(JOB_ID);
            assertThat(saved.getAnalysisPromptVersion()).isEqualTo("requirement_reanalyze_v2");

            // RequirementReview는 절대 조회/수정하지 않음
            then(reviewRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("기존 insight가 있으면 overwrite하고 review는 그대로")
        void overwriteExistingInsight_reviewUntouched() {
            AnalysisJob job = buildReanalyzeJob(REQUIREMENT_ID);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));
            givenRequirementAndSources();
            given(openAiClient.chat(anyString(), anyString())).willReturn(MOCK_GPT_RESPONSE);

            RequirementInsight existingInsight = RequirementInsight.builder()
                    .requirementId(REQUIREMENT_ID)
                    .factSummary("이전 분석 결과")
                    .factLevel(FactLevel.REVIEW_NEEDED)
                    .generatedByJobId("old-job-id")
                    .build();
            given(insightRepository.findByRequirementId(REQUIREMENT_ID))
                    .willReturn(Optional.of(existingInsight));
            given(insightRepository.save(any(RequirementInsight.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            worker.onJobCreated(new AnalysisJobEvent(
                    JOB_ID, PROJECT_ID, DOCUMENT_ID, "REQUIREMENT_INSIGHT_REANALYZE"));

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);

            // 덮어쓰기 검증
            ArgumentCaptor<RequirementInsight> captor = ArgumentCaptor.forClass(RequirementInsight.class);
            then(insightRepository).should().save(captor.capture());
            RequirementInsight saved = captor.getValue();
            assertThat(saved.getFactSummary()).contains("p.5"); // 이전 값 → 새 값
            assertThat(saved.getFactLevel()).isEqualTo(FactLevel.FACT);
            assertThat(saved.getGeneratedByJobId()).isEqualTo(JOB_ID); // old-job-id → JOB_ID

            // review 미접근
            then(reviewRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("폴링으로도 PENDING Job을 수거하여 처리")
        void pollingPicksUpPendingJob() {
            AnalysisJob job = buildReanalyzeJob(REQUIREMENT_ID);
            given(jobRepository.findPendingJobs()).willReturn(List.of(job));
            given(jobRepository.findStuckRunningJobs(any())).willReturn(List.of());
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));
            givenRequirementAndSources();
            given(openAiClient.chat(anyString(), anyString())).willReturn(MOCK_GPT_RESPONSE);
            given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
            given(insightRepository.save(any(RequirementInsight.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            worker.pollPendingJobs();

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.COMPLETED);
            assertThat(job.getResultCount()).isEqualTo(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 실패 케이스
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("실패 흐름")
    class FailureFlow {

        @Test
        @DisplayName("targetRequirementId null → 즉시 FAILED (non-retryable)")
        void nullTargetRequirementId_fails() {
            AnalysisJob job = buildReanalyzeJob(null);
            // non-retryable이므로 retryCount 무관하게 즉시 FAILED
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));

            worker.onJobCreated(new AnalysisJobEvent(
                    JOB_ID, PROJECT_ID, DOCUMENT_ID, "REQUIREMENT_INSIGHT_REANALYZE"));

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("TARGET_REQUIREMENT_MISSING");
            assertThat(job.getFinishedAt()).isNotNull();
        }

        @Test
        @DisplayName("requirement 미존재 → 즉시 FAILED (non-retryable)")
        void requirementNotFound_fails() {
            AnalysisJob job = buildReanalyzeJob(REQUIREMENT_ID);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));
            given(requirementRepository.findById(REQUIREMENT_ID)).willReturn(Optional.empty());

            worker.onJobCreated(new AnalysisJobEvent(
                    JOB_ID, PROJECT_ID, DOCUMENT_ID, "REQUIREMENT_INSIGHT_REANALYZE"));

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("REQUIREMENT_NOT_FOUND");
        }

        @Test
        @DisplayName("source 없음 → 즉시 FAILED (non-retryable)")
        void noSources_fails() {
            AnalysisJob job = buildReanalyzeJob(REQUIREMENT_ID);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));

            Requirement req = Requirement.builder()
                    .id(REQUIREMENT_ID).projectId(PROJECT_ID).documentId(DOCUMENT_ID)
                    .requirementCode("SFR-001").originalText("text")
                    .category(RequirementCategory.FUNCTIONAL).build();
            given(requirementRepository.findById(REQUIREMENT_ID)).willReturn(Optional.of(req));
            given(sourceRepository.findByRequirementIdOrderByLinkTypeAsc(REQUIREMENT_ID))
                    .willReturn(List.of());

            worker.onJobCreated(new AnalysisJobEvent(
                    JOB_ID, PROJECT_ID, DOCUMENT_ID, "REQUIREMENT_INSIGHT_REANALYZE"));

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("SOURCE_NOT_FOUND");
        }

        @Test
        @DisplayName("OpenAI timeout(retryable) → 첫 실패는 재시도(PENDING), review 미변경")
        void openAiTimeout_retries() {
            AnalysisJob job = buildReanalyzeJob(REQUIREMENT_ID);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));
            givenRequirementAndSources();
            given(openAiClient.chat(anyString(), anyString()))
                    .willThrow(new OpenAiException(OpenAiErrorCode.OPENAI_TIMEOUT, "timeout 300s"));

            worker.onJobCreated(new AnalysisJobEvent(
                    JOB_ID, PROJECT_ID, DOCUMENT_ID, "REQUIREMENT_INSIGHT_REANALYZE"));

            // retryable → PENDING 복귀
            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);
            assertThat(job.getRetryCount()).isEqualTo(1);

            // insight, review 모두 미변경
            then(insightRepository).should(never()).save(any());
            then(reviewRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("OpenAI 400(non-retryable) → 즉시 FAILED, 재시도 안 함")
        void openAiBadRequest_noRetry() {
            AnalysisJob job = buildReanalyzeJob(REQUIREMENT_ID);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));
            givenRequirementAndSources();
            given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
            given(openAiClient.chat(anyString(), anyString()))
                    .willThrow(new OpenAiException(OpenAiErrorCode.OPENAI_BAD_REQUEST, 400, "invalid"));

            worker.onJobCreated(new AnalysisJobEvent(
                    JOB_ID, PROJECT_ID, DOCUMENT_ID, "REQUIREMENT_INSIGHT_REANALYZE"));

            // non-retryable → 즉시 FAILED (재시도 0회임에도)
            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("OPENAI_BAD_REQUEST");
            assertThat(job.getRetryCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("GPT 응답 파싱 실패(non-retryable) → 즉시 FAILED")
        void parseFailure_fails() {
            AnalysisJob job = buildReanalyzeJob(REQUIREMENT_ID);
            given(jobRepository.findById(JOB_ID)).willReturn(Optional.of(job));
            givenRequirementAndSources();
            given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
            given(openAiClient.chat(anyString(), anyString())).willReturn("이것은 유효한 JSON이 아닙니다");

            worker.onJobCreated(new AnalysisJobEvent(
                    JOB_ID, PROJECT_ID, DOCUMENT_ID, "REQUIREMENT_INSIGHT_REANALYZE"));

            assertThat(job.getStatus()).isEqualTo(AnalysisJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("OPENAI_PARSE_ERROR");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 핸들러 선택 검증
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("핸들러 선택")
    class HandlerSelection {

        @Test
        @DisplayName("REQUIREMENT_INSIGHT_REANALYZE는 ReanalyzeHandler가 처리")
        void reanalyzeHandlerSelected() {
            assertThat(reanalyzeHandler.supports(buildReanalyzeJob(REQUIREMENT_ID))).isTrue();
        }

        @Test
        @DisplayName("다른 jobType은 ReanalyzeHandler가 거부")
        void reanalyzeHandlerRejectsOtherTypes() {
            AnalysisJob otherJob = AnalysisJob.builder()
                    .jobType(AnalysisJobType.RFP_PARSE).build();
            assertThat(reanalyzeHandler.supports(otherJob)).isFalse();

            AnalysisJob extractJob = AnalysisJob.builder()
                    .jobType(AnalysisJobType.REQUIREMENT_EXTRACTION).build();
            assertThat(reanalyzeHandler.supports(extractJob)).isFalse();
        }
    }

    // ── helper ──────────────────────────────────────────────────────

    private void setRetryCount(AnalysisJob job, int count) {
        try {
            var f = AnalysisJob.class.getDeclaredField("retryCount");
            f.setAccessible(true);
            f.set(job, count);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
