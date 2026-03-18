package com.bidops.domain.project.dto;

import com.bidops.domain.project.enums.ProjectStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /projects/{projectId}/status */
@Getter
@NoArgsConstructor
public class ProjectStatusChangeRequest {

    @NotNull(message = "status는 필수입니다.")
    private ProjectStatus status;
}
