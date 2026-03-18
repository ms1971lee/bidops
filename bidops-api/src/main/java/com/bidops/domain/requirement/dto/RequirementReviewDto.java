package com.bidops.domain.requirement.dto;

import com.bidops.domain.requirement.entity.RequirementReview;
import com.bidops.domain.requirement.enums.RequirementReviewStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 사람 검토 레이어 전용 응답 DTO.
 * RequirementInsightDto(AI 분석)와 명확히 분리.
 */
@Getter
@Builder
public class RequirementReviewDto {

    @JsonProperty("requirement_id")
    private String requirementId;

    @JsonProperty("review_status")
    private RequirementReviewStatus reviewStatus;

    @JsonProperty("review_comment")
    private String reviewComment;

    @JsonProperty("reviewed_by_user_id")
    private String reviewedByUserId;

    @JsonProperty("reviewed_at")
    private LocalDateTime reviewedAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    // ── factory ──────────────────────────────────────────────────────────────
    public static RequirementReviewDto from(RequirementReview r) {
        return RequirementReviewDto.builder()
                .requirementId(r.getRequirementId())
                .reviewStatus(r.getReviewStatus())
                .reviewComment(r.getReviewComment())
                .reviewedByUserId(r.getReviewedByUserId())
                .reviewedAt(r.getReviewedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    public static RequirementReviewDto empty(String requirementId) {
        return RequirementReviewDto.builder()
                .requirementId(requirementId)
                .reviewStatus(RequirementReviewStatus.NOT_REVIEWED)
                .build();
    }
}
