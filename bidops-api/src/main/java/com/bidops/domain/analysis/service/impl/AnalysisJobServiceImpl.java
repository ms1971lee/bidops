package com.bidops.domain.analysis.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.response.ListData;
import com.bidops.domain.analysis.dto.AnalysisJobCreateRequest;
import com.bidops.domain.analysis.dto.AnalysisJobDto;
import com.bidops.domain.analysis.entity.AnalysisJob;
import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.bidops.domain.analysis.repository.AnalysisJobRepository;
import com.bidops.domain.analysis.service.AnalysisJobService;
import com.bidops.domain.analysis.worker.AnalysisJobDispatcher;
import com.bidops.domain.document.repository.DocumentRepository;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisJobServiceImpl implements AnalysisJobService {

    private final AnalysisJobRepository analysisJobRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectActivityService     activityService;
    private final AnalysisJobDispatcher      dispatcher;
    private final DocumentRepository    documentRepository;

    @Override
    public ListData<AnalysisJobDto> listJobs(String projectId, AnalysisJobType jobType, AnalysisJobStatus status) {
        requirePermission(projectId, ProjectPermission.ANALYSIS_VIEW);
        List<AnalysisJobDto> items = analysisJobRepository
                .findByProjectId(projectId, jobType, status)
                .stream().map(AnalysisJobDto::from).toList();
        return new ListData<>(items, items.size());
    }

    @Override
    @Transactional
    public AnalysisJobDto createJob(String projectId, AnalysisJobCreateRequest request) {
        requirePermission(projectId, ProjectPermission.ANALYSIS_RUN);

        // 문서 존재 확인
        documentRepository.findByIdAndProjectIdAndDeletedFalse(request.getDocumentId(), projectId)
                .orElseThrow(() -> BidOpsException.notFound("문서"));

        // 동일 문서에 대해 PENDING/RUNNING 중복 방지
        boolean alreadyRunning = analysisJobRepository
                .findByProjectId(projectId, request.getJobType(), null)
                .stream()
                .filter(j -> request.getDocumentId().equals(j.getDocumentId()))
                .anyMatch(j -> j.getStatus() == AnalysisJobStatus.PENDING
                            || j.getStatus() == AnalysisJobStatus.RUNNING);
        if (alreadyRunning) {
            throw BidOpsException.conflict("해당 문서에 이미 실행 중인 분석 Job이 존재합니다.");
        }

        AnalysisJob job = AnalysisJob.builder()
                .projectId(projectId)
                .documentId(request.getDocumentId())
                .jobType(request.getJobType())
                .build();

        AnalysisJob saved = analysisJobRepository.save(job);

        // 비동기 워커로 디스패치
        dispatcher.dispatch(saved);
        log.info("[AnalysisJob] 생성 및 디스패치 완료 jobId={} type={}", saved.getId(), saved.getJobType());

        activityService.record(projectId, ActivityType.ANALYSIS_STARTED,
                "분석 실행: " + saved.getJobType(),
                com.bidops.auth.SecurityUtils.currentUserId(),
                saved.getId(), "analysis_job", null);

        return AnalysisJobDto.from(saved);
    }

    @Override
    public AnalysisJobDto getJob(String projectId, String jobId) {
        requirePermission(projectId, ProjectPermission.ANALYSIS_VIEW);
        return AnalysisJobDto.from(
                analysisJobRepository.findByIdAndProjectId(jobId, projectId)
                        .orElseThrow(() -> BidOpsException.notFound("분석 Job"))
        );
    }

    @Override
    @Transactional
    public AnalysisJobDto startJob(String projectId, String jobId) {
        AnalysisJob job = analysisJobRepository.findByIdAndProjectId(jobId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("분석 Job"));
        job.start();
        return AnalysisJobDto.from(job);
    }

    @Override
    @Transactional
    public AnalysisJobDto completeJob(String projectId, String jobId, int resultCount) {
        AnalysisJob job = analysisJobRepository.findByIdAndProjectId(jobId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("분석 Job"));
        job.complete(resultCount);
        activityService.record(projectId, ActivityType.ANALYSIS_COMPLETED,
                "분석 완료: " + job.getJobType() + " (" + resultCount + "건)",
                com.bidops.auth.SecurityUtils.currentUserId(), jobId, "analysis_job", null);
        return AnalysisJobDto.from(job);
    }

    @Override
    @Transactional
    public AnalysisJobDto failJob(String projectId, String jobId, String errorCode, String errorMessage) {
        AnalysisJob job = analysisJobRepository.findByIdAndProjectId(jobId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("분석 Job"));
        job.fail(errorCode, errorMessage);
        activityService.record(projectId, ActivityType.ANALYSIS_FAILED,
                "분석 실패: " + job.getJobType() + " - " + errorMessage,
                com.bidops.auth.SecurityUtils.currentUserId(), jobId, "analysis_job", errorMessage);
        return AnalysisJobDto.from(job);
    }

    @Override
    @Transactional
    public AnalysisJobDto retryJob(String projectId, String jobId) {
        requirePermission(projectId, ProjectPermission.ANALYSIS_RUN);
        AnalysisJob job = analysisJobRepository.findByIdAndProjectId(jobId, projectId)
                .orElseThrow(() -> BidOpsException.notFound("분석 Job"));
        if (!job.canRetry()) {
            throw BidOpsException.badRequest("재시도할 수 없는 상태입니다. (최대 " + job.getMaxRetries() + "회)");
        }
        job.retry();
        analysisJobRepository.save(job);
        dispatcher.dispatch(job);
        log.info("[AnalysisJob] 수동 재시도: jobId={} retryCount={}", jobId, job.getRetryCount());
        activityService.record(projectId, ActivityType.ANALYSIS_STARTED,
                "분석 재시도: " + job.getJobType() + " (시도 " + job.getRetryCount() + "/" + job.getMaxRetries() + ")",
                com.bidops.auth.SecurityUtils.currentUserId(), jobId, "analysis_job", null);
        return AnalysisJobDto.from(job);
    }

    // ── internal ─────────────────────────────────────────────────────────────
    private void requirePermission(String projectId, ProjectPermission permission) {
        authorizationService.requirePermission(projectId, com.bidops.auth.SecurityUtils.currentUserId(), permission);
    }
}
