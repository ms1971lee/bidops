package com.bidops.domain.analysis.service;

import com.bidops.common.response.ListData;
import com.bidops.domain.analysis.dto.AnalysisJobCreateRequest;
import com.bidops.domain.analysis.dto.AnalysisJobDto;
import com.bidops.domain.analysis.enums.AnalysisJobStatus;
import com.bidops.domain.analysis.enums.AnalysisJobType;

public interface AnalysisJobService {

    /** GET /projects/{projectId}/analysis-jobs */
    ListData<AnalysisJobDto> listJobs(String projectId, AnalysisJobType jobType, AnalysisJobStatus status);

    /** POST /projects/{projectId}/analysis-jobs */
    AnalysisJobDto createJob(String projectId, AnalysisJobCreateRequest request);

    /** GET /projects/{projectId}/analysis-jobs/{jobId} */
    AnalysisJobDto getJob(String projectId, String jobId);

    /** POST /projects/{projectId}/analysis-jobs/{jobId}/start (워커 콜백) */
    AnalysisJobDto startJob(String projectId, String jobId);

    /** POST /projects/{projectId}/analysis-jobs/{jobId}/complete (워커 콜백) */
    AnalysisJobDto completeJob(String projectId, String jobId, int resultCount);

    /** POST /projects/{projectId}/analysis-jobs/{jobId}/fail (워커 콜백) */
    AnalysisJobDto failJob(String projectId, String jobId, String errorCode, String errorMessage);

    /** POST /projects/{projectId}/analysis-jobs/{jobId}/retry (수동 재시도) */
    AnalysisJobDto retryJob(String projectId, String jobId);
}
