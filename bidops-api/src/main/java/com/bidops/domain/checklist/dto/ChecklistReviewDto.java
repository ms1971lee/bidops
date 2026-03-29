package com.bidops.domain.checklist.dto;

import com.bidops.domain.checklist.entity.ChecklistReview;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChecklistReviewDto {

    private String id;

    @JsonProperty("checklist_item_id")
    private String checklistItemId;

    @JsonProperty("change_type")
    private String changeType;

    @JsonProperty("before_value")
    private String beforeValue;

    @JsonProperty("after_value")
    private String afterValue;

    private String comment;

    @JsonProperty("actor_user_id")
    private String actorUserId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static ChecklistReviewDto from(ChecklistReview r) {
        return ChecklistReviewDto.builder()
                .id(r.getId())
                .checklistItemId(r.getChecklistItemId())
                .changeType(r.getChangeType())
                .beforeValue(r.getBeforeValue())
                .afterValue(r.getAfterValue())
                .comment(r.getComment())
                .actorUserId(r.getActorUserId())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
