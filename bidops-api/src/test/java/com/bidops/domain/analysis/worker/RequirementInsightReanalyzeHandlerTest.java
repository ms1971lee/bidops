package com.bidops.domain.analysis.worker;

import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.bidops.domain.analysis.pipeline.OpenAiClient;
import com.bidops.domain.analysis.pipeline.OpenAiErrorCode;
import com.bidops.domain.analysis.pipeline.OpenAiException;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.requirement.entity.Requirement;
import com.bidops.domain.requirement.entity.RequirementInsight;
import com.bidops.domain.requirement.entity.RequirementSource;
import com.bidops.domain.requirement.enums.FactLevel;
import com.bidops.domain.requirement.enums.RequirementCategory;
import com.bidops.domain.requirement.repository.RequirementInsightRepository;
import com.bidops.domain.requirement.repository.RequirementRepository;
import com.bidops.domain.requirement.repository.RequirementSourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RequirementInsightReanalyzeHandlerTest {

    @Mock RequirementRepository requirementRepository;
    @Mock RequirementInsightRepository insightRepository;
    @Mock RequirementSourceRepository sourceRepository;
    @Mock SourceExcerptRepository excerptRepository;
    @Mock OpenAiClient openAiClient;
    @Spy  ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks RequirementInsightReanalyzeHandler sut;

    static final String JOB_ID = "job-1";
    static final String PROJECT_ID = "proj-1";
    static final String REQUIREMENT_ID = "req-1";
    static final String DOCUMENT_ID = "doc-1";

    private AnalysisJob job;

    @BeforeEach
    void setUp() {
        job = AnalysisJob.builder()
                .id(JOB_ID)
                .projectId(PROJECT_ID)
                .documentId(DOCUMENT_ID)
                .targetRequirementId(REQUIREMENT_ID)
                .jobType(AnalysisJobType.REQUIREMENT_INSIGHT_REANALYZE)
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

    private static final String MOCK_GPT_RESPONSE = """
            {
              "fact_summary": "p.5 조항 3.1.2에 실시간 모니터링 요구 명시",
              "interpretation_summary": "발주처는 운영 중 장애를 조기 탐지하기 위한 실시간 모니터링 체계를 요구. 평가위원은 구체적 모니터링 항목과 대응 체계를 확인할 것으로 예상.",
              "intent_summary": "운영 장애 조기 탐지를 위한 실시간 모니터링 체계 구축",
              "proposal_point": "Grafana 기반 대시보드를 구축하여 CPU/메모리/디스크/네트워크 4대 지표를 5초 주기로 수집하고 임계치 초과 시 자동 알림 발송",
              "implementation_approach": "1)관리대상: 서버/네트워크/애플리케이션 2)수행주체: 운영팀 3)절차: 수집→분석→알림→대응 4)주기: 5초 5)도구: Prometheus+Grafana 6)산출물: 모니터링 운영계획서 7)검증: 월간 점검보고서",
              "expected_deliverables": ["모니터링 운영계획서", "장애 대응 매뉴얼", "월간 모니터링 보고서"],
              "differentiation_point": "AIOps 기반 이상 징후 사전 예측으로 장애 발생 30분 전 사전 경고",
              "risk_note": ["모니터링 대상 증가 시 수집 지연 발생 가능: 수집 주기 동적 조절 메커니즘 적용", "알림 폭주 시 운영자 피로 증가: 알림 그룹핑 및 에스컬레이션 정책 수립"],
              "evaluation_focus": "모니터링 대상 범위, 수집 주기, 알림 정책, 장애 대응 프로세스의 구체성",
              "required_evidence": "모니터링 대시보드 샘플 화면, 장애 대응 프로세스 흐름도, 유사 프로젝트 운영 실적",
              "draft_proposal_snippet": "본 사업에서는 Prometheus 기반 메트릭 수집과 Grafana 대시보드를 활용하여 실시간 모니터링 체계를 구축합니다. 서버 4대 핵심 지표를 5초 주기로 수집하며, 임계치 초과 시 Slack/SMS 알림을 자동 발송합니다. AIOps 모듈을 통해 이상 패턴을 사전 감지하여 장애 예방 효과를 극대화합니다.",
              "clarification_questions": null,
              "query_needed": false,
              "fact_level": "FACT"
            }
            """;

    // ── supports ────────────────────────────────────────────────────────

    @Test
    @DisplayName("REQUIREMENT_INSIGHT_REANALYZE만 supports")
    void supports_onlyReanalyze() {
        assertThat(sut.supports(job)).isTrue();

        AnalysisJob otherJob = AnalysisJob.builder()
                .jobType(AnalysisJobType.RFP_PARSE).build();
        assertThat(sut.supports(otherJob)).isFalse();
    }

    // ── 정상 실행 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 실행: OpenAI 호출 → insight overwrite")
    void execute_success() {
        givenRequirementAndSources();
        given(openAiClient.chat(anyString(), anyString())).willReturn(MOCK_GPT_RESPONSE);
        given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
        given(insightRepository.save(any(RequirementInsight.class)))
                .willAnswer(inv -> inv.getArgument(0));

        int result = sut.execute(job, percent -> {});

        assertThat(result).isEqualTo(1);

        // insight 저장 검증
        ArgumentCaptor<RequirementInsight> captor = ArgumentCaptor.forClass(RequirementInsight.class);
        then(insightRepository).should().save(captor.capture());
        RequirementInsight saved = captor.getValue();

        assertThat(saved.getFactSummary()).contains("p.5");
        assertThat(saved.getFactLevel()).isEqualTo(FactLevel.FACT);
        assertThat(saved.getProposalPoint()).contains("Grafana");
        assertThat(saved.getGeneratedByJobId()).isEqualTo(JOB_ID);
        assertThat(saved.getAnalysisPromptVersion()).isEqualTo("requirement_reanalyze_v2");
        assertThat(saved.isQueryNeeded()).isFalse();
    }

    @Test
    @DisplayName("기존 insight가 있으면 overwrite")
    void execute_overwriteExistingInsight() {
        givenRequirementAndSources();
        given(openAiClient.chat(anyString(), anyString())).willReturn(MOCK_GPT_RESPONSE);

        RequirementInsight existing = RequirementInsight.builder()
                .requirementId(REQUIREMENT_ID)
                .factSummary("이전 분석")
                .factLevel(FactLevel.REVIEW_NEEDED)
                .generatedByJobId("old-job")
                .build();
        given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.of(existing));
        given(insightRepository.save(any(RequirementInsight.class)))
                .willAnswer(inv -> inv.getArgument(0));

        sut.execute(job, percent -> {});

        ArgumentCaptor<RequirementInsight> captor = ArgumentCaptor.forClass(RequirementInsight.class);
        then(insightRepository).should().save(captor.capture());
        RequirementInsight saved = captor.getValue();

        // 기존 값이 덮어쓰였는지 확인
        assertThat(saved.getFactSummary()).contains("p.5");
        assertThat(saved.getFactLevel()).isEqualTo(FactLevel.FACT);
        assertThat(saved.getGeneratedByJobId()).isEqualTo(JOB_ID);
    }

    // ── 실패 케이스 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("targetRequirementId null → OpenAiException(TARGET_REQUIREMENT_MISSING, non-retryable)")
    void execute_nullTargetRequirementId() {
        AnalysisJob nullTargetJob = AnalysisJob.builder()
                .id(JOB_ID).projectId(PROJECT_ID).documentId(DOCUMENT_ID)
                .targetRequirementId(null)
                .jobType(AnalysisJobType.REQUIREMENT_INSIGHT_REANALYZE).build();

        assertThatThrownBy(() -> sut.execute(nullTargetJob, percent -> {}))
                .isInstanceOf(OpenAiException.class)
                .satisfies(e -> {
                    OpenAiException oae = (OpenAiException) e;
                    assertThat(oae.getErrorCode()).isEqualTo(OpenAiErrorCode.TARGET_REQUIREMENT_MISSING);
                    assertThat(oae.isRetryable()).isFalse();
                });
    }

    @Test
    @DisplayName("targetRequirementId blank → OpenAiException(TARGET_REQUIREMENT_MISSING)")
    void execute_blankTargetRequirementId() {
        AnalysisJob blankTargetJob = AnalysisJob.builder()
                .id(JOB_ID).projectId(PROJECT_ID).documentId(DOCUMENT_ID)
                .targetRequirementId("   ")
                .jobType(AnalysisJobType.REQUIREMENT_INSIGHT_REANALYZE).build();

        assertThatThrownBy(() -> sut.execute(blankTargetJob, percent -> {}))
                .isInstanceOf(OpenAiException.class)
                .satisfies(e -> assertThat(((OpenAiException) e).getErrorCode())
                        .isEqualTo(OpenAiErrorCode.TARGET_REQUIREMENT_MISSING));
    }

    @Test
    @DisplayName("requirement 없으면 OpenAiException(REQUIREMENT_NOT_FOUND, non-retryable)")
    void execute_requirementNotFound() {
        given(requirementRepository.findById(REQUIREMENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute(job, percent -> {}))
                .isInstanceOf(OpenAiException.class)
                .satisfies(e -> {
                    OpenAiException oae = (OpenAiException) e;
                    assertThat(oae.getErrorCode()).isEqualTo(OpenAiErrorCode.REQUIREMENT_NOT_FOUND);
                    assertThat(oae.isRetryable()).isFalse();
                });
    }

    @Test
    @DisplayName("source 없으면 OpenAiException(SOURCE_NOT_FOUND, non-retryable)")
    void execute_noSources() {
        Requirement req = Requirement.builder()
                .id(REQUIREMENT_ID).projectId(PROJECT_ID).documentId(DOCUMENT_ID)
                .requirementCode("SFR-001").originalText("text").category(RequirementCategory.FUNCTIONAL)
                .build();
        given(requirementRepository.findById(REQUIREMENT_ID)).willReturn(Optional.of(req));
        given(sourceRepository.findByRequirementIdOrderByLinkTypeAsc(REQUIREMENT_ID))
                .willReturn(List.of());

        assertThatThrownBy(() -> sut.execute(job, percent -> {}))
                .isInstanceOf(OpenAiException.class)
                .satisfies(e -> {
                    OpenAiException oae = (OpenAiException) e;
                    assertThat(oae.getErrorCode()).isEqualTo(OpenAiErrorCode.SOURCE_NOT_FOUND);
                    assertThat(oae.isRetryable()).isFalse();
                });
    }

    @Test
    @DisplayName("OpenAI timeout → OpenAiException(OPENAI_TIMEOUT, retryable)")
    void execute_openAiTimeout_retryable() {
        givenRequirementAndSources();
        given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
        given(openAiClient.chat(anyString(), anyString()))
                .willThrow(new OpenAiException(OpenAiErrorCode.OPENAI_TIMEOUT, "timeout after 300s"));

        assertThatThrownBy(() -> sut.execute(job, percent -> {}))
                .isInstanceOf(OpenAiException.class)
                .satisfies(e -> {
                    OpenAiException oae = (OpenAiException) e;
                    assertThat(oae.getErrorCode()).isEqualTo(OpenAiErrorCode.OPENAI_TIMEOUT);
                    assertThat(oae.isRetryable()).isTrue();
                });
        // insight 미변경
        then(insightRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("OpenAI 429 → OpenAiException(OPENAI_RATE_LIMIT, retryable)")
    void execute_openAiRateLimit_retryable() {
        givenRequirementAndSources();
        given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
        given(openAiClient.chat(anyString(), anyString()))
                .willThrow(new OpenAiException(OpenAiErrorCode.OPENAI_RATE_LIMIT, 429, "rate limited"));

        assertThatThrownBy(() -> sut.execute(job, percent -> {}))
                .isInstanceOf(OpenAiException.class)
                .satisfies(e -> assertThat(((OpenAiException) e).isRetryable()).isTrue());
    }

    @Test
    @DisplayName("OpenAI 400 → OpenAiException(OPENAI_BAD_REQUEST, non-retryable)")
    void execute_openAiBadRequest_nonRetryable() {
        givenRequirementAndSources();
        given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
        given(openAiClient.chat(anyString(), anyString()))
                .willThrow(new OpenAiException(OpenAiErrorCode.OPENAI_BAD_REQUEST, 400, "invalid prompt"));

        assertThatThrownBy(() -> sut.execute(job, percent -> {}))
                .isInstanceOf(OpenAiException.class)
                .satisfies(e -> assertThat(((OpenAiException) e).isRetryable()).isFalse());
    }

    // ── 파싱 검증 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("fact_level이 알 수 없는 값이면 REVIEW_NEEDED fallback")
    void parseResponse_unknownFactLevel() {
        String response = """
                { "fact_summary": "test", "fact_level": "UNKNOWN_VALUE" }
                """;

        var fields = sut.parseResponse(response);
        assertThat(fields.factLevel()).isEqualTo(FactLevel.REVIEW_NEEDED);
    }

    @Test
    @DisplayName("expected_deliverables가 문자열이면 배열로 변환")
    void parseResponse_deliverablesAsString() {
        String response = """
                { "fact_summary": "test", "fact_level": "FACT",
                  "expected_deliverables": "산출물A, 산출물B, 산출물C" }
                """;

        var fields = sut.parseResponse(response);
        assertThat(fields.expectedDeliverablesJson()).contains("산출물A");
        assertThat(fields.expectedDeliverablesJson()).contains("산출물B");
    }

    @Test
    @DisplayName("userPrompt에 requirement 원문과 source excerpt가 포함됨")
    void buildUserPrompt_containsSourceInfo() {
        Requirement req = Requirement.builder()
                .requirementCode("SFR-001")
                .originalText("시스템은 실시간 모니터링을 제공해야 한다.")
                .category(RequirementCategory.FUNCTIONAL)
                .build();

        SourceExcerpt excerpt = SourceExcerpt.builder()
                .id("excerpt-1").documentId(DOCUMENT_ID).pageNo(5)
                .excerptType(SourceExcerpt.ExcerptType.PARAGRAPH)
                .anchorLabel("3.1.2")
                .rawText("시스템은 실시간 모니터링을 제공해야 한다.")
                .build();

        RequirementSource rs = RequirementSource.builder()
                .requirementId(REQUIREMENT_ID).sourceExcerptId("excerpt-1")
                .linkType(RequirementSource.LinkType.PRIMARY)
                .build();

        String prompt = sut.buildUserPrompt(req, List.of(rs), java.util.Map.of("excerpt-1", excerpt));

        assertThat(prompt).contains("SFR-001");
        assertThat(prompt).contains("실시간 모니터링");
        assertThat(prompt).contains("page_no=5");
        assertThat(prompt).contains("anchor_label=3.1.2");
        assertThat(prompt).contains("PRIMARY");
    }

    // ── 품질 검증 ─────────────────────────────────────────────────────

    static final String SAMPLE_ORIGINAL = "시스템은 실시간 모니터링을 제공해야 한다.";

    @Test
    @DisplayName("품질 양호: 모든 필드 충분 → factLevel 유지")
    void validateQuality_goodResponse_keepFactLevel() {
        String good = """
                {
                  "fact_summary": "p.5 조항 3.1.2에 실시간 모니터링 요구가 명시되어 있음",
                  "interpretation_summary": "발주처는 운영 장애 조기 탐지를 위해 모니터링을 요구함. 평가위원은 대시보드 구체성을 확인할 것임. 이전 프로젝트에서 장애 대응 지연을 경험했을 가능성이 높음.",
                  "intent_summary": "운영 장애 조기 탐지를 위한 실시간 모니터링 체계 구축",
                  "proposal_point": "Prometheus+Grafana 기반 대시보드를 구축하여 4대 핵심 지표를 5초 주기로 수집하고, 임계치 초과 시 자동 알림 체계를 운영합니다.",
                  "implementation_approach": "1)관리대상: 서버/네트워크/애플리케이션 2)수행주체: 운영팀 3)절차: 수집→분석→알림→대응 4)주기: 5초 5)도구: Prometheus+Grafana 6)산출물: 모니터링 운영계획서 7)검증: 월간 KPI 점검",
                  "expected_deliverables": ["모니터링 운영계획서", "장애 대응 매뉴얼"],
                  "differentiation_point": "AIOps 기반 이상 징후 사전 예측으로 장애 발생 30분 전 자동 경고 체계를 구축하여 MTTR을 50% 단축",
                  "risk_note": ["모니터링 대상 급증 시 수집 지연: 수집 주기 동적 조절 메커니즘 적용"],
                  "evaluation_focus": "모니터링 범위, 수집 주기, 알림 정책의 구체성",
                  "required_evidence": "대시보드 샘플 화면, 프로세스 흐름도",
                  "draft_proposal_snippet": "본 사업에서는 Prometheus 기반 메트릭 수집과 Grafana 대시보드를 활용하여 실시간 모니터링 체계를 구축합니다. 서버 4대 핵심 지표를 5초 주기로 수집하며, 임계치 초과 시 자동 알림을 발송합니다.",
                  "clarification_questions": null,
                  "query_needed": false,
                  "fact_level": "FACT"
                }
                """;
        var fields = sut.validateQuality(sut.parseResponse(good), SAMPLE_ORIGINAL);
        assertThat(fields.factLevel()).isEqualTo(FactLevel.FACT);
    }

    @Test
    @DisplayName("필드 대부분 누락(치명 + 일반 혼합) → REVIEW_NEEDED 강등")
    void validateQuality_emptyFields_downgradeToReviewNeeded() {
        String empty = """
                {
                  "fact_summary": "짧음",
                  "interpretation_summary": "",
                  "intent_summary": "",
                  "proposal_point": null,
                  "implementation_approach": null,
                  "expected_deliverables": [],
                  "differentiation_point": "",
                  "risk_note": [],
                  "evaluation_focus": "",
                  "required_evidence": "",
                  "draft_proposal_snippet": "",
                  "query_needed": false,
                  "fact_level": "FACT"
                }
                """;
        var fields = sut.validateQuality(sut.parseResponse(empty), SAMPLE_ORIGINAL);
        assertThat(fields.factLevel()).isEqualTo(FactLevel.REVIEW_NEEDED);
    }

    @Test
    @DisplayName("치명: query_needed=true + clarification 없음 → 단독 강등")
    void validateQuality_critical_queryNeeded_noClarification() {
        String response = """
                {
                  "fact_summary": "p.5 조항 3.1.2에 모니터링 요구사항이 명시됨",
                  "interpretation_summary": "발주처는 장애 조기 탐지를 위해 모니터링을 요구함. 평가위원은 대시보드 구체성을 확인함. 이전 프로젝트 경험 반영.",
                  "intent_summary": "실시간 모니터링 체계 구축 필요",
                  "proposal_point": "Prometheus 기반 모니터링 대시보드를 구축하여 5초 주기 수집 및 자동 알림 체계를 운영합니다.",
                  "implementation_approach": "1)서버 2)운영팀 3)수집→알림 4)5초 5)Prometheus 6)운영계획서 7)월간점검",
                  "expected_deliverables": ["운영계획서", "점검보고서"],
                  "differentiation_point": "AIOps 기반 이상 징후 예측으로 MTTR 50% 단축 목표",
                  "risk_note": ["수집 지연: 동적 조절 메커니즘 적용"],
                  "evaluation_focus": "모니터링 범위와 알림 정책 구체성 확인",
                  "required_evidence": "대시보드 샘플 화면",
                  "draft_proposal_snippet": "Prometheus 기반 모니터링 체계를 구축합니다. 5초 주기로 수집하며 자동 알림을 제공합니다. MTTR 50% 단축 효과가 기대됩니다.",
                  "query_needed": true,
                  "clarification_questions": null,
                  "fact_level": "FACT"
                }
                """;
        var fields = sut.validateQuality(sut.parseResponse(response), SAMPLE_ORIGINAL);
        // 치명 이슈 1건 → 즉시 REVIEW_NEEDED
        assertThat(fields.factLevel()).isEqualTo(FactLevel.REVIEW_NEEDED);
    }

    @Test
    @DisplayName("치명: risk_note 0건 → 단독 강등")
    void validateQuality_critical_riskZero_downgrade() {
        String response = """
                {
                  "fact_summary": "p.5 조항 3.1.2에 모니터링 요구사항이 명시되어 있음",
                  "interpretation_summary": "발주처는 장애 탐지를 원함. 평가위원은 구체성을 확인함. 과거 장애 대응 지연 경험 반영.",
                  "intent_summary": "실시간 모니터링 체계 구축 필요성",
                  "proposal_point": "Prometheus 기반 모니터링 대시보드를 구축하여 5초 주기 수집 및 자동 알림 체계를 운영합니다.",
                  "implementation_approach": "1)서버 2)운영팀 3)수집→알림 4)5초 5)Prometheus 6)운영계획서 7)월간점검",
                  "expected_deliverables": ["운영계획서", "점검보고서"],
                  "differentiation_point": "AIOps 기반 이상 징후 예측으로 MTTR 50% 단축",
                  "risk_note": [],
                  "evaluation_focus": "모니터링 범위와 알림 정책 확인",
                  "required_evidence": "대시보드 샘플 화면",
                  "draft_proposal_snippet": "Prometheus 기반 모니터링을 구축합니다. 5초 주기 수집과 자동 알림을 제공합니다. MTTR 50% 단축이 기대됩니다.",
                  "query_needed": false,
                  "fact_level": "FACT"
                }
                """;
        var fields = sut.validateQuality(sut.parseResponse(response), SAMPLE_ORIGINAL);
        assertThat(fields.factLevel()).isEqualTo(FactLevel.REVIEW_NEEDED);
    }

    @Test
    @DisplayName("일반 이슈 1건만 → factLevel 유지")
    void validateQuality_oneMinorIssue_keepFactLevel() {
        String response = """
                {
                  "fact_summary": "p.5 조항 3.1.2에 모니터링 요구사항이 명시되어 있음",
                  "interpretation_summary": "발주처는 장애 조기 탐지를 위해 모니터링을 요구함. 평가위원은 대시보드 구체성을 확인함. 이전 프로젝트 경험 반영.",
                  "intent_summary": "실시간 모니터링 체계 구축 필요",
                  "proposal_point": "Prometheus 기반 모니터링 대시보드를 구축하여 5초 주기 수집 및 자동 알림 체계를 운영합니다.",
                  "implementation_approach": "1)서버 2)운영팀 3)수집→알림 4)5초 5)Prometheus 6)운영계획서 7)월간점검",
                  "expected_deliverables": ["운영계획서", "점검보고서"],
                  "differentiation_point": "AIOps 기반 이상 징후 예측으로 MTTR 50% 단축 목표",
                  "risk_note": ["수집 지연: 동적 조절 메커니즘 적용"],
                  "evaluation_focus": "짧음",
                  "required_evidence": "대시보드 샘플 화면",
                  "draft_proposal_snippet": "Prometheus 기반 모니터링 체계를 구축합니다. 5초 주기로 수집하며 자동 알림을 제공합니다. MTTR 50% 단축 효과가 기대됩니다.",
                  "query_needed": false,
                  "fact_level": "FACT"
                }
                """;
        var fields = sut.validateQuality(sut.parseResponse(response), SAMPLE_ORIGINAL);
        // 일반 이슈 1건(evaluation_focus 부족)만 → 유지
        assertThat(fields.factLevel()).isEqualTo(FactLevel.FACT);
    }

    @Test
    @DisplayName("일반 이슈 2건 → REVIEW_NEEDED 강등")
    void validateQuality_twoMinorIssues_downgrade() {
        String response = """
                {
                  "fact_summary": "p.5 조항 3.1.2에 모니터링 요구사항이 명시되어 있음",
                  "interpretation_summary": "발주처는 장애 조기 탐지를 위해 모니터링을 요구함. 평가위원은 대시보드 구체성을 확인함. 이전 프로젝트 경험 반영.",
                  "intent_summary": "실시간 모니터링 체계 구축 필요",
                  "proposal_point": "Prometheus 기반 모니터링 대시보드를 구축하여 5초 주기 수집 및 자동 알림 체계를 운영합니다.",
                  "implementation_approach": "1)서버 2)운영팀 3)수집→알림 4)5초 5)Prometheus 6)운영계획서 7)월간점검",
                  "expected_deliverables": ["운영계획서"],
                  "differentiation_point": "AIOps 기반 이상 징후 예측으로 MTTR 50% 단축",
                  "risk_note": ["수집 지연: 동적 조절 메커니즘 적용"],
                  "evaluation_focus": "짧음",
                  "required_evidence": "대시보드 샘플 화면",
                  "draft_proposal_snippet": "Prometheus 기반 모니터링을 구축합니다. 5초 주기 수집과 자동 알림을 제공합니다. MTTR 50% 단축이 기대됩니다.",
                  "query_needed": false,
                  "fact_level": "FACT"
                }
                """;
        var fields = sut.validateQuality(sut.parseResponse(response), SAMPLE_ORIGINAL);
        // 일반 이슈 2건(evaluation_focus 부족 + deliverables 2건 미만) → 강등
        assertThat(fields.factLevel()).isEqualTo(FactLevel.REVIEW_NEEDED);
    }

    @Test
    @DisplayName("치명: proposal_point가 원문 반복 → 단독 강등")
    void validateQuality_critical_proposalRepeatsOriginal() {
        String longOriginal = "본 시스템은 실시간 서버 모니터링 대시보드를 제공하여 운영 중 장애를 조기에 탐지하고 대응할 수 있는 체계를 구축해야 한다.";
        String response = String.format("""
                {
                  "fact_summary": "p.5에 모니터링 요구사항이 명시되어 있음을 확인함",
                  "interpretation_summary": "발주처는 장애 조기 탐지를 위해 모니터링을 요구함. 평가위원은 구체성 확인. 경험 반영.",
                  "intent_summary": "실시간 모니터링 체계 구축 필요성 확인됨",
                  "proposal_point": "%s",
                  "implementation_approach": "1)서버 2)운영팀 3)수집→알림 4)5초 5)Prometheus 6)운영계획서 7)월간점검",
                  "expected_deliverables": ["운영계획서", "점검보고서"],
                  "differentiation_point": "AIOps 기반 이상 징후 예측으로 MTTR 50%% 단축 목표",
                  "risk_note": ["수집 지연: 동적 조절 메커니즘 적용"],
                  "evaluation_focus": "모니터링 범위와 알림 정책 구체성 확인",
                  "required_evidence": "대시보드 샘플 화면 제공 예정",
                  "draft_proposal_snippet": "Prometheus 기반 모니터링 체계를 구축합니다. 5초 주기로 수집하며 자동 알림을 제공합니다. MTTR 50%% 단축 효과.",
                  "query_needed": false,
                  "fact_level": "FACT"
                }
                """, longOriginal);
        var fields = sut.validateQuality(sut.parseResponse(response), longOriginal);
        assertThat(fields.factLevel()).isEqualTo(FactLevel.REVIEW_NEEDED);
    }

    @Test
    @DisplayName("resemblesOriginal: 동일 텍스트 → true")
    void resemblesOriginal_identical() {
        String text = "본 시스템은 실시간 서버 모니터링 대시보드를 제공하여 운영 중 장애를 조기에 탐지하고 대응할 수 있는 체계를 구축해야 한다.";
        assertThat(RequirementInsightReanalyzeHandler.resemblesOriginal(text, text)).isTrue();
    }

    @Test
    @DisplayName("resemblesOriginal: 완전히 다른 텍스트 → false")
    void resemblesOriginal_different() {
        String original = "본 시스템은 실시간 서버 모니터링 대시보드를 제공하여 운영 중 장애를 조기에 탐지하고 대응할 수 있는 체계를 구축해야 한다.";
        String output = "Prometheus와 Grafana를 활용한 통합 관제 체계를 구축하여 CPU, 메모리, 디스크, 네트워크 지표를 5초 간격으로 수집합니다.";
        assertThat(RequirementInsightReanalyzeHandler.resemblesOriginal(output, original)).isFalse();
    }

    // ── 캐시 hit / miss ─────────────────────────────────────────────

    @Test
    @DisplayName("캐시 히트: 동일 fingerprint + 동일 promptVersion → OpenAI 미호출, resultCount=0")
    void execute_cacheHit_skipsOpenAi() {
        givenRequirementAndSources();

        // 먼저 fingerprint 계산
        Requirement req = requirementRepository.findById(REQUIREMENT_ID).orElseThrow();
        var sources = sourceRepository.findByRequirementIdOrderByLinkTypeAsc(REQUIREMENT_ID);
        var excerptMap = java.util.Map.of("excerpt-1",
                excerptRepository.findAllByIdInOrdered(List.of("excerpt-1")).get(0));
        String fingerprint = sut.computeFingerprint(req, sources, excerptMap);

        // 기존 insight에 동일 fingerprint + promptVersion 설정
        RequirementInsight cached = RequirementInsight.builder()
                .requirementId(REQUIREMENT_ID)
                .factSummary("이전 분석")
                .factLevel(FactLevel.FACT)
                .inputFingerprint(fingerprint)
                .analysisPromptVersion(RequirementInsightReanalyzeHandler.PROMPT_VERSION)
                .build();
        // insightRepository.findByRequirementId 재설정 (lenient로 기존 stub 덮어쓰기)
        lenient().when(insightRepository.findByRequirementId(REQUIREMENT_ID)).thenReturn(Optional.of(cached));

        int result = sut.execute(job, percent -> {});

        assertThat(result).isEqualTo(0); // cache hit → 0
        assertThat(job.isCacheHit()).isTrue();

        // OpenAI 호출 없음
        then(openAiClient).shouldHaveNoInteractions();
        // insight save 없음 (기존 그대로)
        then(insightRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("캐시 미스: fingerprint 다름 → OpenAI 호출, resultCount=1")
    void execute_cacheMiss_differentFingerprint() {
        givenRequirementAndSources();
        given(openAiClient.chat(anyString(), anyString())).willReturn(MOCK_GPT_RESPONSE);

        RequirementInsight stale = RequirementInsight.builder()
                .requirementId(REQUIREMENT_ID)
                .factSummary("이전 분석")
                .inputFingerprint("old-different-fingerprint")
                .analysisPromptVersion(RequirementInsightReanalyzeHandler.PROMPT_VERSION)
                .build();
        given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.of(stale));
        given(insightRepository.save(any(RequirementInsight.class)))
                .willAnswer(inv -> inv.getArgument(0));

        int result = sut.execute(job, percent -> {});

        assertThat(result).isEqualTo(1); // cache miss → 1
        assertThat(job.isCacheHit()).isFalse();
        then(openAiClient).should().chat(anyString(), anyString());

        ArgumentCaptor<RequirementInsight> captor = ArgumentCaptor.forClass(RequirementInsight.class);
        then(insightRepository).should().save(captor.capture());
        assertThat(captor.getValue().getInputFingerprint()).isNotEqualTo("old-different-fingerprint");
    }

    @Test
    @DisplayName("캐시 미스: promptVersion 다름 → OpenAI 호출")
    void execute_cacheMiss_differentPromptVersion() {
        givenRequirementAndSources();
        given(openAiClient.chat(anyString(), anyString())).willReturn(MOCK_GPT_RESPONSE);

        // fingerprint는 같지만 promptVersion이 다름
        Requirement req = requirementRepository.findById(REQUIREMENT_ID).orElseThrow();
        var sources = sourceRepository.findByRequirementIdOrderByLinkTypeAsc(REQUIREMENT_ID);
        var excerptMap = java.util.Map.of("excerpt-1",
                excerptRepository.findAllByIdInOrdered(List.of("excerpt-1")).get(0));
        String fingerprint = sut.computeFingerprint(req, sources, excerptMap);

        RequirementInsight oldVersion = RequirementInsight.builder()
                .requirementId(REQUIREMENT_ID)
                .inputFingerprint(fingerprint)
                .analysisPromptVersion("old_prompt_v0") // 다른 버전
                .build();
        lenient().when(insightRepository.findByRequirementId(REQUIREMENT_ID)).thenReturn(Optional.of(oldVersion));
        given(insightRepository.save(any(RequirementInsight.class)))
                .willAnswer(inv -> inv.getArgument(0));

        int result = sut.execute(job, percent -> {});

        assertThat(result).isEqualTo(1);
        then(openAiClient).should().chat(anyString(), anyString());
    }

    @Test
    @DisplayName("캐시 미스: 기존 insight 없음 → OpenAI 호출")
    void execute_cacheMiss_noExistingInsight() {
        givenRequirementAndSources();
        given(openAiClient.chat(anyString(), anyString())).willReturn(MOCK_GPT_RESPONSE);
        given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
        given(insightRepository.save(any(RequirementInsight.class)))
                .willAnswer(inv -> inv.getArgument(0));

        int result = sut.execute(job, percent -> {});

        assertThat(result).isEqualTo(1);
        then(openAiClient).should().chat(anyString(), anyString());

        ArgumentCaptor<RequirementInsight> captor = ArgumentCaptor.forClass(RequirementInsight.class);
        then(insightRepository).should().save(captor.capture());
        assertThat(captor.getValue().getInputFingerprint()).isNotNull();
        assertThat(captor.getValue().getInputFingerprint()).hasSize(64); // SHA-256 hex
    }

    @Test
    @DisplayName("fingerprint는 결정적이다: 같은 입력이면 항상 같은 값")
    void computeFingerprint_deterministic() {
        Requirement req = Requirement.builder()
                .originalText("시스템은 실시간 모니터링을 제공해야 한다.")
                .category(RequirementCategory.FUNCTIONAL)
                .build();
        SourceExcerpt excerpt = SourceExcerpt.builder()
                .id("e1").documentId(DOCUMENT_ID).pageNo(5)
                .excerptType(SourceExcerpt.ExcerptType.PARAGRAPH)
                .anchorLabel("3.1.2")
                .rawText("원문 텍스트")
                .build();
        RequirementSource rs = RequirementSource.builder()
                .sourceExcerptId("e1").linkType(RequirementSource.LinkType.PRIMARY).build();

        var map = java.util.Map.of("e1", excerpt);
        String fp1 = sut.computeFingerprint(req, List.of(rs), map);
        String fp2 = sut.computeFingerprint(req, List.of(rs), map);

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(64);
    }
}
