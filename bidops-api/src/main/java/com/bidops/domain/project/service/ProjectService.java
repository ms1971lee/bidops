package com.bidops.domain.project.service;

import com.bidops.common.response.ListData;
import com.bidops.domain.project.dto.*;
import com.bidops.domain.project.enums.ProjectStatus;

public interface ProjectService {

    ListData<ProjectDto> listProjects(String userId, String keyword, ProjectStatus status, int page, int size);

    ProjectDto createProject(String userId, ProjectCreateRequest request);

    ProjectDto getProject(String userId, String projectId);

    ProjectDto updateProject(String userId, String projectId, ProjectUpdateRequest request);

    ProjectDto changeProjectStatus(String userId, String projectId, ProjectStatusChangeRequest request);

    /** 프로젝트 멤버 여부 확인. 다른 도메인 서비스에서 호출. */
    void validateAccess(String userId, String projectId);
}
