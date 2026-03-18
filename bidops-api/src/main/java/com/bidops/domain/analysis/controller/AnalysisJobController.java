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
@RequiredArgsConstructor
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
}
