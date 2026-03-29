package com.bidops.domain.analysis.controller;

import com.bidops.common.response.ApiResponse;
import com.bidops.common.response.ListData;
import com.bidops.common.response.MetaDto;
import com.bidops.domain.analysis.dto.AnalysisJobCreateRequest;
import com.bidops.domain.analysis.dto.AnalysisJobDto;
import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;
import com.bidops.domain.analysis.service.AnalysisJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/analysis-jobs")
@Tag(name = "AnalysisJobs")
public class AnalysisJobController {

    private final AnalysisJobService analysisJobService;

    /**
     * GET /projects/{projectId}/analysis-jobs
     * operationId: listAnalysisJobs
     */
    @GetMapping
    @Operation(summary = "프로젝트 기준 Job 목록 조회", operationId = "listAnalysisJobs")
    public ApiResponse<ListData<AnalysisJobDto>> listJobs(
            @PathVariable String projectId,
            @RequestParam(name = "job_type", required = false) AnalysisJobType jobType,
            @RequestParam(name = "status",   required = false) AnalysisJobStatus status) {

        ListData<AnalysisJobDto> data = analysisJobService.listJobs(projectId, jobType, status);
        return ApiResponse.ok(data, MetaDto.of(1, (int) data.getTotalCount(), data.getTotalCount()));
    }

    /**
     * POST /projects/{projectId}/analysis-jobs
     * operationId: createAnalysisJob
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "분석 시작 (Job 생성)", operationId = "createAnalysisJob")
    public ApiResponse<AnalysisJobDto> createJob(
            @PathVariable String projectId,
            @RequestBody @Valid AnalysisJobCreateRequest request) {

        return ApiResponse.ok(analysisJobService.createJob(projectId, request));
    }

    /**
     * GET /projects/{projectId}/analysis-jobs/{jobId}
     * operationId: getAnalysisJob
     */
    @GetMapping("/{jobId}")
    @Operation(summary = "Job 상태 조회", operationId = "getAnalysisJob")
    public ApiResponse<AnalysisJobDto> getJob(
            @PathVariable String projectId,
            @PathVariable String jobId) {

        return ApiResponse.ok(analysisJobService.getJob(projectId, jobId));
    }

    // ── 워커 콜백 엔드포인트 ──────────────────────────────────────────

    @PostMapping("/{jobId}/start")
    @Operation(summary = "Job 시작 (워커 콜백)", operationId = "startAnalysisJob")
    public ApiResponse<AnalysisJobDto> startJob(
            @PathVariable String projectId,
            @PathVariable String jobId) {
        return ApiResponse.ok(analysisJobService.startJob(projectId, jobId));
    }

    @PostMapping("/{jobId}/complete")
    @Operation(summary = "Job 완료 (워커 콜백)", operationId = "completeAnalysisJob")
    public ApiResponse<AnalysisJobDto> completeJob(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @RequestBody java.util.Map<String, Object> body) {
        int resultCount = body.containsKey("result_count") ? ((Number) body.get("result_count")).intValue() : 0;
        return ApiResponse.ok(analysisJobService.completeJob(projectId, jobId, resultCount));
    }

    @PostMapping("/{jobId}/fail")
    @Operation(summary = "Job 실패 (워커 콜백)", operationId = "failAnalysisJob")
    public ApiResponse<AnalysisJobDto> failJob(
            @PathVariable String projectId,
            @PathVariable String jobId,
            @RequestBody java.util.Map<String, String> body) {
        return ApiResponse.ok(analysisJobService.failJob(projectId, jobId,
                body.getOrDefault("error_code", "UNKNOWN"),
                body.getOrDefault("error_message", "알 수 없는 오류")));
    }

    @PostMapping("/{jobId}/retry")
    @Operation(summary = "Job 수동 재시도", operationId = "retryAnalysisJob")
    public ApiResponse<AnalysisJobDto> retryJob(
            @PathVariable String projectId,
            @PathVariable String jobId) {
        return ApiResponse.ok(analysisJobService.retryJob(projectId, jobId));
    }

    /**
     * GET /projects/{projectId}/analysis-jobs/coverage
     * 최신 커버리지 감사 결과 조회
     */
    @GetMapping("/coverage")
    @Operation(summary = "커버리지 감사 결과 조회", operationId = "getCoverageAudit")
    public ApiResponse<com.bidops.domain.analysis.entity.CoverageAudit> getCoverageAudit(
            @PathVariable String projectId,
            @RequestParam(name = "document_id") String documentId) {
        return ApiResponse.ok(coverageAuditRepository.findTopByDocumentIdOrderByCreatedAtDesc(documentId)
                .orElse(null));
    }

    private final com.bidops.domain.analysis.repository.CoverageAuditRepository coverageAuditRepository;

    public AnalysisJobController(AnalysisJobService analysisJobService,
                                  com.bidops.domain.analysis.repository.CoverageAuditRepository coverageAuditRepository) {
        this.analysisJobService = analysisJobService;
        this.coverageAuditRepository = coverageAuditRepository;
    }
}
