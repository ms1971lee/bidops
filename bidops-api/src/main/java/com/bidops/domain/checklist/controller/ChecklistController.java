package com.bidops.domain.checklist.controller;

import com.bidops.common.response.ApiResponse;
import com.bidops.domain.checklist.dto.*;
import com.bidops.domain.checklist.enums.ChecklistItemStatus;
import com.bidops.domain.checklist.enums.RiskLevel;
import com.bidops.domain.checklist.service.ChecklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/checklists")
@RequiredArgsConstructor
@Tag(name = "Checklists", description = "누락 방지 체크리스트 API")
public class ChecklistController {

    private final ChecklistService checklistService;

    // ── 체크리스트 묶음 ─────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "체크리스트 목록 조회", operationId = "listChecklists")
    public ApiResponse<List<ChecklistDto>> listChecklists(@PathVariable String projectId) {
        return ApiResponse.ok(checklistService.listChecklists(projectId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "체크리스트 생성", operationId = "createChecklist")
    public ApiResponse<ChecklistDto> createChecklist(
            @PathVariable String projectId,
            @RequestBody @Valid ChecklistCreateRequest request) {
        return ApiResponse.ok(checklistService.createChecklist(projectId, request));
    }

    @GetMapping("/{checklistId}")
    @Operation(summary = "체크리스트 상세 조회", operationId = "getChecklist")
    public ApiResponse<ChecklistDto> getChecklist(
            @PathVariable String projectId,
            @PathVariable String checklistId) {
        return ApiResponse.ok(checklistService.getChecklist(projectId, checklistId));
    }

    // ── 체크리스트 항목 ─────────────────────────────────────────────────

    @GetMapping("/{checklistId}/items")
    @Operation(summary = "체크리스트 항목 목록 조회", operationId = "listChecklistItems")
    public ApiResponse<List<ChecklistItemDto>> listItems(
            @PathVariable String projectId,
            @PathVariable String checklistId,
            @RequestParam(required = false) ChecklistItemStatus status,
            @RequestParam(name = "risk_level", required = false) RiskLevel riskLevel,
            @RequestParam(required = false) Boolean mandatory,
            @RequestParam(name = "requirement_id", required = false) String requirementId) {
        return ApiResponse.ok(checklistService.listItems(checklistId, status, riskLevel, mandatory, requirementId));
    }

    @PostMapping("/{checklistId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "체크리스트 항목 생성", operationId = "createChecklistItem")
    public ApiResponse<ChecklistItemDto> createItem(
            @PathVariable String projectId,
            @PathVariable String checklistId,
            @RequestBody @Valid ChecklistItemCreateRequest request) {
        return ApiResponse.ok(checklistService.createItem(checklistId, request));
    }

    @GetMapping("/{checklistId}/items/{itemId}")
    @Operation(summary = "체크리스트 항목 상세 조회", operationId = "getChecklistItem")
    public ApiResponse<ChecklistItemDto> getItem(
            @PathVariable String projectId,
            @PathVariable String checklistId,
            @PathVariable String itemId) {
        return ApiResponse.ok(checklistService.getItem(checklistId, itemId));
    }

    @PatchMapping("/{checklistId}/items/{itemId}")
    @Operation(summary = "체크리스트 항목 수정", operationId = "updateChecklistItem")
    public ApiResponse<ChecklistItemDto> updateItem(
            @PathVariable String projectId,
            @PathVariable String checklistId,
            @PathVariable String itemId,
            @RequestBody @Valid ChecklistItemUpdateRequest request) {
        return ApiResponse.ok(checklistService.updateItem(checklistId, itemId, request));
    }

    @PostMapping("/{checklistId}/items/{itemId}/status")
    @Operation(summary = "체크리스트 항목 상태 변경 (완료 처리 등)", operationId = "changeChecklistItemStatus")
    public ApiResponse<ChecklistItemDto> changeItemStatus(
            @PathVariable String projectId,
            @PathVariable String checklistId,
            @PathVariable String itemId,
            @RequestBody @Valid ChecklistItemStatusChangeRequest request) {
        return ApiResponse.ok(checklistService.changeItemStatus(checklistId, itemId, request));
    }
}
