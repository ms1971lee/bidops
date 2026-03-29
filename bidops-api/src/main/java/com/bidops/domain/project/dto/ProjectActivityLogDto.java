package com.bidops.domain.project.dto;

import com.bidops.domain.project.entity.ProjectActivityLog;
import com.bidops.domain.project.enums.ActivityType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProjectActivityLogDto {

    private String id;

    @JsonProperty("activity_type")
    private ActivityType activityType;

    private String summary;

    @JsonProperty("actor_user_id")
    private String actorUserId;

    @JsonProperty("actor_name")
    private String actorName;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("target_type")
    private String targetType;

    private String detail;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static ProjectActivityLogDto from(ProjectActivityLog a, String actorName) {
        return ProjectActivityLogDto.builder()
                .id(a.getId())
                .activityType(a.getActivityType())
                .summary(a.getSummary())
                .actorUserId(a.getActorUserId())
                .actorName(actorName)
                .targetId(a.getTargetId())
                .targetType(a.getTargetType())
                .detail(a.getDetail())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
