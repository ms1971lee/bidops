package com.bidops.domain.requirement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 프로젝트 내 requirement별 최근 재분석 상태 맵.
 * key: requirementId, value: 최근 job 상태 요약.
 */
@Getter
@Builder
public class ReanalyzeStatusMapDto {

    /** requirementId → 최근 재분석 상태 */
    private Map<String, ReqReanalyzeStatus> items;

    @Getter
    @Builder
    public static class ReqReanalyzeStatus {
        private String status;       // PENDING, RUNNING, COMPLETED, FAILED
        @JsonProperty("cache_hit")
        private boolean cacheHit;
        @JsonProperty("error_message")
        private String errorMessage;
        @JsonProperty("error_code")
        private String errorCode;
        @JsonProperty("finished_at")
        private LocalDateTime finishedAt;
        @JsonProperty("progress")
        private int progress;
        @JsonProperty("progress_step")
        private String progressStep;
    }
}
