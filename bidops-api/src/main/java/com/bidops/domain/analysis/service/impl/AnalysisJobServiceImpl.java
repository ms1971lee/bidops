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
import com.bidops.domain.document.repository.DocumentRepository;
import com.bidops.domain.project.service.ProjectService;
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
    private final ProjectService        projectService;
    private final DocumentRepository    documentRepository;

    // TODO: 비동기 워커 연동 시 주입
    // private final AnalysisWorkerClient workerClient;

    @Override
    public ListData<AnalysisJobDto> listJobs(String projectId, AnalysisJobType jobType, AnalysisJobStatus status) {
        validateProject(projectId);
        List<AnalysisJobDto> items = analysisJobRepository
                .findByProjectId(projectId, jobType, status)
                .stream().map(AnalysisJobDto::from).toList();
        return new ListData<>(items, items.size());
    }

    @Override
    @Transactional
    public AnalysisJobDto createJob(String projectId, AnalysisJobCreateRequest request) {
        validateProject(projectId);

        // 문서 존재 확인
        documentRepository.findByIdAndProjectIdAndDeletedFalse(request.getDocumentId(), projectId)
                .orElseThrow(() -> BidOpsException.notFound("문서"));

        // 동일 타입 PENDING/RUNNING 중복 방지
        boolean alreadyRunning = analysisJobRepository
                .findByProjectId(projectId, request.getJobType(), null)
                .stream()
                .anyMatch(j -> j.getStatus() == AnalysisJobStatus.PENDING
                            || j.getStatus() == AnalysisJobStatus.RUNNING);
        if (alreadyRunning) {
            throw BidOpsException.conflict("이미 실행 중인 동일 타입의 분석 Job이 존재합니다.");
        }

        AnalysisJob job = AnalysisJob.builder()
                .projectId(projectId)
                .documentId(request.getDocumentId())
                .jobType(request.getJobType())
                .build();

        AnalysisJob saved = analysisJobRepository.save(job);

        // TODO: 비동기 워커로 Job 전달
        log.info("[AnalysisJob] 생성 완료 jobId={} type={}", saved.getId(), saved.getJobType());

        return AnalysisJobDto.from(saved);
    }

    @Override
    public AnalysisJobDto getJob(String projectId, String jobId) {
        return AnalysisJobDto.from(
                analysisJobRepository.findByIdAndProjectId(jobId, projectId)
                        .orElseThrow(() -> BidOpsException.notFound("분석 Job"))
        );
    }

    // ── internal ─────────────────────────────────────────────────────────────
    private void validateProject(String projectId) {
        projectService.validateAccess(com.bidops.auth.SecurityUtils.currentUserId(), projectId);
    }
}
