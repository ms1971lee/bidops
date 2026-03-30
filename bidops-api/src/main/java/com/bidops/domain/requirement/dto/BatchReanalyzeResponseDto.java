package com.bidops.domain.requirement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BatchReanalyzeResponseDto {

    @JsonProperty("requested_count")
    private int requestedCount;

    @JsonProperty("created_job_count")
    private int createdJobCount;

    @JsonProperty("skipped_count")
    private int skippedCount;

    @JsonProperty("created_job_ids")
    private List<String> createdJobIds;

    @JsonProperty("skipped_reasons")
    private List<SkipReason> skippedReasons;

    @Getter
    @Builder
    public static class SkipReason {
        @JsonProperty("requirement_id")
        private String requirementId;
        private String reason;
    }
}
