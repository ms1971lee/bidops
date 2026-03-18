package com.bidops.domain.inquiry.controller;

import com.bidops.common.response.ApiResponse;
import com.bidops.domain.inquiry.dto.*;
import com.bidops.domain.inquiry.enums.InquiryPriority;
import com.bidops.domain.inquiry.enums.InquiryStatus;
import com.bidops.domain.inquiry.service.InquiryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/inquiries")
@RequiredArgsConstructor
@Tag(name = "Inquiries", description = "발주처 질의 관리 API")
public class InquiryController {

    private final InquiryService inquiryService;

    @GetMapping
    @Operation(summary = "질의 목록 조회", operationId = "listInquiries")
    public ApiResponse<List<InquiryDto>> list(
            @PathVariable String projectId,
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) InquiryPriority priority,
            @RequestParam(name = "requirement_id", required = false) String requirementId) {
        return ApiResponse.ok(inquiryService.listInquiries(projectId, status, priority, requirementId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "질의 생성", operationId = "createInquiry")
    public ApiResponse<InquiryDto> create(
            @PathVariable String projectId,
            @RequestBody @Valid InquiryCreateRequest request) {
        return ApiResponse.ok(inquiryService.createInquiry(projectId, request));
    }

    @GetMapping("/{inquiryId}")
    @Operation(summary = "질의 상세 조회", operationId = "getInquiry")
    public ApiResponse<InquiryDto> get(
            @PathVariable String projectId,
            @PathVariable String inquiryId) {
        return ApiResponse.ok(inquiryService.getInquiry(projectId, inquiryId));
    }

    @PatchMapping("/{inquiryId}")
    @Operation(summary = "질의 수정", operationId = "updateInquiry")
    public ApiResponse<InquiryDto> update(
            @PathVariable String projectId,
            @PathVariable String inquiryId,
            @RequestBody @Valid InquiryUpdateRequest request) {
        return ApiResponse.ok(inquiryService.updateInquiry(projectId, inquiryId, request));
    }

    @PostMapping("/{inquiryId}/status")
    @Operation(summary = "질의 상태 변경", operationId = "changeInquiryStatus")
    public ApiResponse<InquiryDto> changeStatus(
            @PathVariable String projectId,
            @PathVariable String inquiryId,
            @RequestBody @Valid InquiryStatusChangeRequest request) {
        return ApiResponse.ok(inquiryService.changeStatus(projectId, inquiryId, request));
    }
}
