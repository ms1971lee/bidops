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
}
