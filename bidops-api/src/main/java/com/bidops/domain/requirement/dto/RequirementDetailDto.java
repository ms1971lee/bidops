package com.bidops.domain.requirement.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * GET /requirements/{requirementId} 응답.
 *
 * requirement : 원문/분류 정보
 * insight     : AI 분석 결과  (fact_level 포함)
 * review      : 사람 검토 결과 (review_status, comment 등)
 */
@Getter
@Builder
public class RequirementDetailDto {

    private RequirementDto        requirement;
    private RequirementInsightDto insight;
    private RequirementReviewDto  review;

    public static RequirementDetailDto of(RequirementDto req,
                                          RequirementInsightDto insight,
                                          RequirementReviewDto review) {
        return RequirementDetailDto.builder()
                .requirement(req)
                .insight(insight)
                .review(review)
                .build();
    }
}
