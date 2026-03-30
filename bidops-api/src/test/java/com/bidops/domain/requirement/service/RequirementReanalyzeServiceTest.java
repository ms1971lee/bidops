package com.bidops.domain.requirement.service;

import com.bidops.common.exception.BidOpsException;
import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.bidops.domain.analysis.repository.AnalysisJobRepository;
import com.bidops.domain.analysis.worker.AnalysisJobDispatcher;
import com.bidops.domain.document.entity.SourceExcerpt;
import com.bidops.domain.document.repository.SourceExcerptRepository;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import com.bidops.domain.requirement.dto.RequirementReanalyzeResponseDto;
import com.bidops.domain.requirement.entity.*;
import com.bidops.domain.requirement.enums.*;
import com.bidops.domain.requirement.repository.*;
import com.bidops.domain.requirement.service.impl.RequirementServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class RequirementReanalyzeServiceTest {

    @Mock RequirementRepository requirementRepository;
    @Mock RequirementInsightRepository insightRepository;
    @Mock RequirementReviewRepository reviewRepository;
    @Mock RequirementSourceRepository sourceRepository;
    @Mock SourceExcerptRepository excerptRepository;
    @Mock AnalysisJobRepository analysisJobRepository;
    @Mock AnalysisJobDispatcher jobDispatcher;
    @Mock ProjectAuthorizationService authorizationService;
    @Mock ProjectActivityService activityService;
    @Mock ObjectMapper objectMapper;

    @InjectMocks RequirementServiceImpl sut;

    static final String PROJECT_ID = "proj-1";
    static final String REQUIREMENT_ID = "req-1";
    static final String DOCUMENT_ID = "doc-1";
    static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, Collections.emptyList()));
    }

    private Requirement givenRequirement() {
        Requirement req = Requirement.builder()
                .id(REQUIREMENT_ID)
                .projectId(PROJECT_ID)
                .documentId(DOCUMENT_ID)
                .requirementCode("SFR-001")
                .originalText("시스템은 실시간 모니터링을 제공해야 한다.")
                .category(RequirementCategory.FUNCTIONAL)
                .build();
        given(requirementRepository.findByIdAndProjectId(REQUIREMENT_ID, PROJECT_ID))
                .willReturn(Optional.of(req));
        return req;
    }

    private void givenSources() {
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

    private void givenNoInsightNoReview() {
        given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
        given(reviewRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("정상 재분석: AnalysisJob 생성 + PENDING 상태 + review 유지")
    void reanalyze_success() {
        givenRequirement();
        givenSources();
        givenNoInsightNoReview();
        given(analysisJobRepository.save(any(AnalysisJob.class)))
                .willAnswer(inv -> inv.getArgument(0));

        RequirementReanalyzeResponseDto result =
                sut.reanalyzeRequirementInsight(PROJECT_ID, REQUIREMENT_ID);

        // 응답 검증
        assertThat(result.getRequirementId()).isEqualTo(REQUIREMENT_ID);
        assertThat(result.getAnalysisStatus()).isEqualTo(AnalysisJobStatus.PENDING);
        assertThat(result.getReview()).isNotNull();
        assertThat(result.getReview().getReviewStatus()).isEqualTo(RequirementReviewStatus.NOT_REVIEWED);

        // AnalysisJob 저장 검증
        ArgumentCaptor<AnalysisJob> jobCaptor = ArgumentCaptor.forClass(AnalysisJob.class);
        then(analysisJobRepository).should().save(jobCaptor.capture());
        AnalysisJob savedJob = jobCaptor.getValue();
        assertThat(savedJob.getJobType()).isEqualTo(AnalysisJobType.REQUIREMENT_INSIGHT_REANALYZE);
        assertThat(savedJob.getProjectId()).isEqualTo(PROJECT_ID);
        assertThat(savedJob.getDocumentId()).isEqualTo(DOCUMENT_ID);
        assertThat(savedJob.getTargetRequirementId()).isEqualTo(REQUIREMENT_ID);
        assertThat(savedJob.getStatus()).isEqualTo(AnalysisJobStatus.PENDING);

        // 이벤트 디스패치 검증
        then(jobDispatcher).should().dispatch(any(AnalysisJob.class));
    }

    @Test
    @DisplayName("기존 review가 있으면 변경 없이 그대로 반환")
    void reanalyze_preservesExistingReview() {
        givenRequirement();
        givenSources();
        given(insightRepository.findByRequirementId(REQUIREMENT_ID)).willReturn(Optional.empty());

        RequirementReview existingReview = RequirementReview.builder()
                .requirementId(REQUIREMENT_ID)
                .reviewStatus(RequirementReviewStatus.APPROVED)
                .reviewComment("검토 완료")
                .reviewedByUserId("reviewer-1")
                .build();
        given(reviewRepository.findByRequirementId(REQUIREMENT_ID))
                .willReturn(Optional.of(existingReview));
        given(analysisJobRepository.save(any(AnalysisJob.class)))
                .willAnswer(inv -> inv.getArgument(0));

        RequirementReanalyzeResponseDto result =
                sut.reanalyzeRequirementInsight(PROJECT_ID, REQUIREMENT_ID);

        assertThat(result.getReview().getReviewStatus()).isEqualTo(RequirementReviewStatus.APPROVED);
        assertThat(result.getReview().getReviewComment()).isEqualTo("검토 완료");
        assertThat(result.getReview().getReviewedByUserId()).isEqualTo("reviewer-1");
    }

    @Test
    @DisplayName("requirement 미존재 시 404")
    void reanalyze_requirementNotFound() {
        given(requirementRepository.findByIdAndProjectId(REQUIREMENT_ID, PROJECT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.reanalyzeRequirementInsight(PROJECT_ID, REQUIREMENT_ID))
                .isInstanceOf(BidOpsException.class)
                .extracting(e -> ((BidOpsException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("source 없으면 400")
    void reanalyze_noSources() {
        givenRequirement();
        given(sourceRepository.findByRequirementIdOrderByLinkTypeAsc(REQUIREMENT_ID))
                .willReturn(Collections.emptyList());

        assertThatThrownBy(() -> sut.reanalyzeRequirementInsight(PROJECT_ID, REQUIREMENT_ID))
                .isInstanceOf(BidOpsException.class)
                .satisfies(e -> {
                    BidOpsException be = (BidOpsException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(be.getMessage()).contains("원문 근거");
                });
    }

    @Test
    @DisplayName("다른 projectId의 requirement 접근 시 404")
    void reanalyze_wrongProjectId() {
        given(requirementRepository.findByIdAndProjectId(REQUIREMENT_ID, "wrong-project"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.reanalyzeRequirementInsight("wrong-project", REQUIREMENT_ID))
                .isInstanceOf(BidOpsException.class)
                .extracting(e -> ((BidOpsException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PENDING 상태의 재분석 Job이 있으면 409 CONFLICT")
    void reanalyze_duplicatePending_conflict() {
        givenRequirement();
        AnalysisJob pendingJob = AnalysisJob.builder()
                .id("existing-job-1")
                .projectId(PROJECT_ID)
                .documentId(DOCUMENT_ID)
                .targetRequirementId(REQUIREMENT_ID)
                .jobType(AnalysisJobType.REQUIREMENT_INSIGHT_REANALYZE)
                .status(AnalysisJobStatus.PENDING)
                .build();
        given(analysisJobRepository.findActiveReanalyzeJobs(REQUIREMENT_ID))
                .willReturn(List.of(pendingJob));

        assertThatThrownBy(() -> sut.reanalyzeRequirementInsight(PROJECT_ID, REQUIREMENT_ID))
                .isInstanceOf(BidOpsException.class)
                .satisfies(e -> {
                    BidOpsException be = (BidOpsException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getMessage()).contains("existing-job-1");
                });

        // Job 생성/디스패치 없음
        then(analysisJobRepository).should(never()).save(any());
        then(jobDispatcher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("RUNNING 상태의 재분석 Job이 있으면 409 CONFLICT")
    void reanalyze_duplicateRunning_conflict() {
        givenRequirement();
        AnalysisJob runningJob = AnalysisJob.builder()
                .id("running-job-1")
                .projectId(PROJECT_ID)
                .documentId(DOCUMENT_ID)
                .targetRequirementId(REQUIREMENT_ID)
                .jobType(AnalysisJobType.REQUIREMENT_INSIGHT_REANALYZE)
                .status(AnalysisJobStatus.RUNNING)
                .build();
        given(analysisJobRepository.findActiveReanalyzeJobs(REQUIREMENT_ID))
                .willReturn(List.of(runningJob));

        assertThatThrownBy(() -> sut.reanalyzeRequirementInsight(PROJECT_ID, REQUIREMENT_ID))
                .isInstanceOf(BidOpsException.class)
                .satisfies(e -> {
                    BidOpsException be = (BidOpsException) e;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getMessage()).contains("running-job-1");
                    assertThat(be.getMessage()).contains("RUNNING");
                });
    }

    @Test
    @DisplayName("COMPLETED/FAILED 이후에는 재분석 가능")
    void reanalyze_afterCompletedOrFailed_allowed() {
        givenRequirement();
        givenSources();
        givenNoInsightNoReview();
        // 활성 Job 없음 (COMPLETED/FAILED는 findActiveReanalyzeJobs에 잡히지 않음)
        given(analysisJobRepository.findActiveReanalyzeJobs(REQUIREMENT_ID))
                .willReturn(Collections.emptyList());
        given(analysisJobRepository.save(any(AnalysisJob.class)))
                .willAnswer(inv -> inv.getArgument(0));

        RequirementReanalyzeResponseDto result =
                sut.reanalyzeRequirementInsight(PROJECT_ID, REQUIREMENT_ID);

        assertThat(result.getRequirementId()).isEqualTo(REQUIREMENT_ID);
        assertThat(result.getAnalysisStatus()).isEqualTo(AnalysisJobStatus.PENDING);
        then(analysisJobRepository).should().save(any(AnalysisJob.class));
    }

    @Test
    @DisplayName("다른 requirement의 PENDING Job은 차단에 영향 없음")
    void reanalyze_otherRequirementPending_allowed() {
        givenRequirement();
        givenSources();
        givenNoInsightNoReview();
        // 이 requirementId에 대한 활성 Job은 없음
        given(analysisJobRepository.findActiveReanalyzeJobs(REQUIREMENT_ID))
                .willReturn(Collections.emptyList());
        given(analysisJobRepository.save(any(AnalysisJob.class)))
                .willAnswer(inv -> inv.getArgument(0));

        RequirementReanalyzeResponseDto result =
                sut.reanalyzeRequirementInsight(PROJECT_ID, REQUIREMENT_ID);

        assertThat(result.getAnalysisStatus()).isEqualTo(AnalysisJobStatus.PENDING);
    }

    @Test
    @DisplayName("활동 기록이 남는지 확인")
    void reanalyze_recordsActivity() {
        givenRequirement();
        givenSources();
        givenNoInsightNoReview();
        given(analysisJobRepository.save(any(AnalysisJob.class)))
                .willAnswer(inv -> inv.getArgument(0));

        sut.reanalyzeRequirementInsight(PROJECT_ID, REQUIREMENT_ID);

        then(activityService).should().record(
                eq(PROJECT_ID), eq(com.bidops.domain.project.enums.ActivityType.REQUIREMENT_INSIGHT_REANALYZED),
                contains("SFR-001"),
                eq(USER_ID), eq(REQUIREMENT_ID), eq("requirement"), anyString());
    }
}
