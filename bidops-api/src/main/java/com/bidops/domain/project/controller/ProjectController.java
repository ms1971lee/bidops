package com.bidops.domain.project.controller;

import com.bidops.common.response.ApiResponse;
import com.bidops.common.response.ListData;
import com.bidops.common.response.MetaDto;
import com.bidops.domain.project.dto.*;
import com.bidops.domain.project.enums.ProjectStatus;
import com.bidops.domain.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "Projects")
public class ProjectController {

    private final ProjectService projectService;

    /**
     * GET /projects
     * operationId: listProjects
     */
    @GetMapping
    @Operation(summary = "프로젝트 목록 조회", operationId = "listProjects")
    public ApiResponse<ListData<ProjectDto>> listProjects(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        ListData<ProjectDto> data = projectService.listProjects(keyword, status, page, size);
        MetaDto meta = MetaDto.of(page, size, data.getTotalCount());
        return ApiResponse.ok(data, meta);
    }

    /**
     * POST /projects
     * operationId: createProject
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "프로젝트 생성", operationId = "createProject")
    public ApiResponse<ProjectDto> createProject(
            @RequestBody @Valid ProjectCreateRequest request) {
        return ApiResponse.ok(projectService.createProject(request));
    }

    /**
     * GET /projects/{projectId}
     * operationId: getProject
     */
    @GetMapping("/{projectId}")
    @Operation(summary = "프로젝트 상세 조회", operationId = "getProject")
    public ApiResponse<ProjectDto> getProject(@PathVariable String projectId) {
        return ApiResponse.ok(projectService.getProject(projectId));
    }

    /**
     * PATCH /projects/{projectId}
     * operationId: updateProject
     */
    @PatchMapping("/{projectId}")
    @Operation(summary = "프로젝트 수정", operationId = "updateProject")
    public ApiResponse<ProjectDto> updateProject(
            @PathVariable String projectId,
            @RequestBody @Valid ProjectUpdateRequest request) {
        return ApiResponse.ok(projectService.updateProject(projectId, request));
    }

    /**
     * POST /projects/{projectId}/status
     * operationId: changeProjectStatus
     */
    @PostMapping("/{projectId}/status")
    @Operation(summary = "프로젝트 상태 변경", operationId = "changeProjectStatus")
    public ApiResponse<ProjectDto> changeProjectStatus(
            @PathVariable String projectId,
            @RequestBody @Valid ProjectStatusChangeRequest request) {
        return ApiResponse.ok(projectService.changeProjectStatus(projectId, request));
    }
}
