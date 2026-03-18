package com.bidops.domain.requirement.dto;

import com.bidops.domain.requirement.enums.RequirementReviewStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /requirements/{requirementId}/review-status */
@Getter
@NoArgsConstructor
public class RequirementReviewStatusChangeRequest {
    @NotNull(message = "review_status는 필수입니다.")
    @JsonProperty("review_status")  private RequirementReviewStatus reviewStatus;
    @JsonProperty("review_comment") private String reviewComment;
}
