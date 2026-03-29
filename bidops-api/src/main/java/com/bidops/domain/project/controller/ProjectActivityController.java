package com.bidops.domain.project.controller;

import com.bidops.auth.SecurityUtils;
import com.bidops.common.response.ApiResponse;
import com.bidops.common.response.ListData;
import com.bidops.common.response.MetaDto;
import com.bidops.domain.project.dto.ProjectActivityLogDto;
import com.bidops.domain.project.enums.ActivityType;
import com.bidops.domain.project.enums.ProjectPermission;
import com.bidops.domain.project.service.ProjectActivityService;
import com.bidops.domain.project.service.ProjectAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RestController
@RequiredArgsConstructor
@Tag(name = "Project Audit Logs", description = "프로젝트 감사로그/활동 이력 API")
public class ProjectActivityController {

    private final ProjectActivityService activityService;
    private final ProjectAuthorizationService authorizationService;

    /**
     * GET /projects/{projectId}/activities  (기존 호환)
     * GET /projects/{projectId}/audit-logs  (정식 경로)
     */
    @GetMapping({
        "/api/v1/projects/{projectId}/activities",
        "/api/v1/projects/{projectId}/audit-logs"
    })
    @Operation(summary = "감사로그 조회", operationId = "listAuditLogs")
    public ApiResponse<ListData<ProjectActivityLogDto>> list(
            @PathVariable String projectId,
            @RequestParam(name = "activity_type", required = false) ActivityType activityType,
            @RequestParam(name = "target_type", required = false) String targetType,
            @RequestParam(name = "actor_user_id", required = false) String actorUserId,
            @RequestParam(name = "date_from", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(name = "date_to", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size) {

        authorizationService.requirePermission(projectId, SecurityUtils.currentUserId(), ProjectPermission.PROJECT_VIEW);

        LocalDateTime from = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime to = dateTo != null ? dateTo.atTime(LocalTime.MAX) : null;

        ListData<ProjectActivityLogDto> data = activityService.listActivities(
                projectId, activityType, targetType, actorUserId, from, to, page, size);
        return ApiResponse.ok(data, MetaDto.of(page, size, data.getTotalCount()));
    }
}
