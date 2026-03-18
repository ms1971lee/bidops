package com.bidops.domain.project.service.impl;

import com.bidops.common.exception.BidOpsException;
import com.bidops.common.response.ListData;
import com.bidops.domain.project.dto.*;
import com.bidops.domain.project.entity.Project;
import com.bidops.domain.project.enums.ProjectStatus;
import com.bidops.domain.project.repository.ProjectRepository;
import com.bidops.domain.project.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;

    @Override
    public ListData<ProjectDto> listProjects(String keyword, ProjectStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ProjectDto> result = projectRepository.search(keyword, status, pageable)
                .map(ProjectDto::from);
        return new ListData<>(result.getContent(), result.getTotalElements());
    }

    @Override
    @Transactional
    public ProjectDto createProject(ProjectCreateRequest request) {
        Project project = Project.builder()
                .name(request.getName())
                .clientName(request.getClientName())
                .businessName(request.getBusinessName())
                .description(request.getDescription())
                .build();
        return ProjectDto.from(projectRepository.save(project));
    }

    @Override
    public ProjectDto getProject(String projectId) {
        return ProjectDto.from(findOrThrow(projectId));
    }

    @Override
    @Transactional
    public ProjectDto updateProject(String projectId, ProjectUpdateRequest request) {
        Project project = findOrThrow(projectId);
        project.update(request.getName(), request.getClientName(),
                       request.getBusinessName(), request.getDescription());
        return ProjectDto.from(project);
    }

    @Override
    @Transactional
    public ProjectDto changeProjectStatus(String projectId, ProjectStatusChangeRequest request) {
        Project project = findOrThrow(projectId);
        project.changeStatus(request.getStatus());
        return ProjectDto.from(project);
    }

    // ── internal ─────────────────────────────────────────────────────────────
    private Project findOrThrow(String projectId) {
        return projectRepository.findByIdAndDeletedFalse(projectId)
                .orElseThrow(() -> BidOpsException.notFound("프로젝트"));
    }
}
