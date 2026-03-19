package com.bidops.domain.project.dto;

import com.bidops.domain.project.entity.ProjectMember;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProjectMemberDto {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("project_role")
    private ProjectMember.ProjectRole projectRole;

    @JsonProperty("user_email")
    private String userEmail;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("joined_at")
    private LocalDateTime joinedAt;

    public static ProjectMemberDto from(ProjectMember m, String email, String name) {
        return ProjectMemberDto.builder()
                .userId(m.getUserId())
                .projectRole(m.getProjectRole())
                .userEmail(email)
                .userName(name)
                .joinedAt(m.getJoinedAt())
                .build();
    }
}
