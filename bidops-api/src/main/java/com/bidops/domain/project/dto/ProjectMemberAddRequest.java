package com.bidops.domain.project.dto;

import com.bidops.domain.project.entity.ProjectMember;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProjectMemberAddRequest {

    @NotBlank(message = "email은 필수입니다")
    private String email;

    @JsonProperty("project_role")
    private ProjectMember.ProjectRole projectRole;
}
