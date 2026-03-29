package com.bidops.domain.project.dto;

import com.bidops.domain.project.entity.ProjectMember.ProjectRole;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberRoleChangeRequest {

    @NotNull(message = "role은 필수입니다")
    private ProjectRole role;
}
