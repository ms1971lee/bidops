package com.bidops.domain.requirement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BatchReanalyzeStatusDto {

    @JsonProperty("total_jobs")
    private int totalJobs;

    @JsonProperty("pending_count")
    private int pendingCount;

    @JsonProperty("running_count")
    private int runningCount;

    @JsonProperty("completed_count")
    private int completedCount;

    @JsonProperty("failed_count")
    private int failedCount;

    @JsonProperty("cache_hit_count")
    private int cacheHitCount;

    /** 모든 job이 COMPLETED 또는 FAILED (더 이상 폴링 불필요) */
    private boolean done;

    /** 실패한 job 목록 (requirementId + errorMessage) */
    @JsonProperty("failed_jobs")
    private List<FailedJob> failedJobs;

    @Getter
    @Builder
    public static class FailedJob {
        @JsonProperty("job_id")
        private String jobId;
        @JsonProperty("requirement_id")
        private String requirementId;
        @JsonProperty("error_code")
        private String errorCode;
        @JsonProperty("error_message")
        private String errorMessage;
    }
}
