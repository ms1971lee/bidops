package com.bidops.domain.requirement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 프로젝트 단위 품질 이슈 통계.
 */
@Getter
@Builder
public class QualityStatsDto {

    /** REVIEW_NEEDED인 requirement 수 */
    @JsonProperty("review_needed_count")
    private int reviewNeededCount;

    /** 전체 requirement 수 (대비 비율 계산용) */
    @JsonProperty("total_requirement_count")
    private int totalRequirementCount;

    /** severity별 이슈 건수 {CRITICAL: N, MINOR: M} */
    @JsonProperty("by_severity")
    private Map<String, Integer> bySeverity;

    /** code별 이슈 건수 (건수 내림차순) */
    @JsonProperty("by_code")
    private List<CodeCount> byCode;

    @Getter
    @Builder
    public static class CodeCount {
        private String code;
        private String severity;
        private String message;
        private int count;
    }
}
