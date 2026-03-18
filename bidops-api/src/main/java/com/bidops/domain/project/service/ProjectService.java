package com.bidops.domain.project.service;

import com.bidops.common.response.ApiResponse;
import com.bidops.common.response.ListData;
import com.bidops.domain.project.dto.*;
import com.bidops.domain.project.enums.ProjectStatus;

public interface ProjectService {

    /** GET /projects */
    ListData<ProjectDto> listProjects(String keyword, ProjectStatus status, int page, int size);

    /** POST /projects */
    ProjectDto createProject(ProjectCreateRequest request);

    /** GET /projects/{projectId} */
    ProjectDto getProject(String projectId);

    /** PATCH /projects/{projectId} */
    ProjectDto updateProject(String projectId, ProjectUpdateRequest request);

    /** POST /projects/{projectId}/status */
    ProjectDto changeProjectStatus(String projectId, ProjectStatusChangeRequest request);
}
