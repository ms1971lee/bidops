package com.bidops.domain.project.controller;

import com.bidops.auth.SecurityUtils;
import com.bidops.common.response.ApiResponse;
import com.bidops.domain.project.dto.ProjectMemberAddRequest;
import com.bidops.domain.project.dto.ProjectMemberDto;
import com.bidops.domain.project.service.ProjectMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/members")
@RequiredArgsConstructor
@Tag(name = "Project Members", description = "프로젝트 멤버 관리 API")
public class ProjectMemberController {

    private final ProjectMemberService memberService;

    @GetMapping
    @Operation(summary = "멤버 목록 조회", operationId = "listProjectMembers")
    public ApiResponse<List<ProjectMemberDto>> list(@PathVariable String projectId) {
        return ApiResponse.ok(memberService.listMembers(SecurityUtils.currentUserId(), projectId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "멤버 초대", operationId = "addProjectMember")
    public ApiResponse<ProjectMemberDto> add(
            @PathVariable String projectId,
            @RequestBody @Valid ProjectMemberAddRequest request) {
        return ApiResponse.ok(memberService.addMember(SecurityUtils.currentUserId(), projectId, request));
    }

    @DeleteMapping("/{targetUserId}")
    @Operation(summary = "멤버 제거", operationId = "removeProjectMember")
    public ApiResponse<Void> remove(
            @PathVariable String projectId,
            @PathVariable String targetUserId) {
        memberService.removeMember(SecurityUtils.currentUserId(), projectId, targetUserId);
        return ApiResponse.ok();
    }
}
