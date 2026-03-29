package com.bidops.domain.project.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.response.ListData;
import com.bidops.common.util.SecurityUtils;
import com.bidops.domain.project.dto.*;
import com.bidops.domain.project.entity.Project;
import com.bidops.domain.project.entity.ProjectMember;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.enums.ProjectStatus;
import com.bidops.domain.project.repository.ProjectMemberRepository;
import com.bidops.domain.project.repository.ProjectRepository;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import com.bidops.domain.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectAuthorizationService authorizationService;
    private final ProjectActivityService activityService;

    @Override
    public ListData<ProjectDto> listProjects(String userId, String keyword, ProjectStatus status, int page, int size) {
        String orgId = SecurityUtils.getCurrentOrganizationId();

        List<String> projectIds = memberRepository.findByUserId(userId)
                .stream().map(ProjectMember::getProjectId).toList();

        if (projectIds.isEmpty()) {
            return new ListData<>(List.of(), 0);
        }

        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProjectDto> result = projectRepository.searchByIdsAndOrganization(
                        projectIds, orgId, keyword, status, pageable)
                .map(ProjectDto::from);
        return new ListData<>(result.getContent(), result.getTotalElements());
    }

    @Override
    @Transactional
    public ProjectDto createProject(String userId, ProjectCreateRequest request) {
        String orgId = SecurityUtils.getCurrentOrganizationId();
        Project project = Project.builder()
                .organizationId(orgId)
                .name(request.getName())
                .clientName(request.getClientName())
                .businessName(request.getBusinessName())
                .description(request.getDescription())
                .build();
        Project saved = projectRepository.save(project);

        memberRepository.save(ProjectMember.builder()
                .projectId(saved.getId())
                .userId(userId)
                .projectRole(ProjectMember.ProjectRole.OWNER)
                .build());

        return ProjectDto.from(saved);
    }

    @Override
    public ProjectDto getProject(String userId, String projectId) {
        authorizationService.requirePermission(projectId, userId, ProjectPermission.PROJECT_VIEW);
        return ProjectDto.from(findOrThrow(projectId));
    }

    @Override
    @Transactional
    public ProjectDto updateProject(String userId, String projectId, ProjectUpdateRequest request) {
        authorizationService.requirePermission(projectId, userId, ProjectPermission.PROJECT_EDIT);
        Project project = findOrThrow(projectId);
        project.update(request.getName(), request.getClientName(),
                       request.getBusinessName(), request.getDescription());
        activityService.record(projectId, ActivityType.PROJECT_UPDATED,
                "프로젝트 수정: " + project.getName(),
                userId, projectId, "project", null);
        return ProjectDto.from(project);
    }

    @Override
    @Transactional
    public ProjectDto changeProjectStatus(String userId, String projectId, ProjectStatusChangeRequest request) {
        authorizationService.requirePermission(projectId, userId, ProjectPermission.PROJECT_EDIT);
        Project project = findOrThrow(projectId);
        project.changeStatus(request.getStatus());
        activityService.record(projectId, ActivityType.PROJECT_UPDATED,
                "프로젝트 상태 변경: " + request.getStatus(),
                userId, projectId, "project", request.getStatus().name());
        return ProjectDto.from(project);
    }

    @Override
    public void validateAccess(String userId, String projectId) {
        authorizationService.requireMembership(projectId, userId);
    }

    private Project findOrThrow(String projectId) {
        return projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> BidOpsException.notFound("프로젝트"));
    }
}
