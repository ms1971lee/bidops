package com.bidops.domain.document.controller;

import com.bidops.common.response.ApiResponse;
import com.bidops.common.response.ListData;
import com.bidops.common.response.MetaDto;
import com.bidops.domain.document.dto.DocumentDto;
import com.bidops.domain.document.dto.DocumentUploadRequest;
import com.bidops.domain.document.enums.DocumentParseStatus;
import com.bidops.domain.document.enums.DocumentType;
import com.bidops.domain.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/documents")
@RequiredArgsConstructor
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    /**
     * GET /projects/{projectId}/documents
     * operationId: listDocuments
     */
    @GetMapping
    @Operation(summary = "문서 목록 조회", operationId = "listDocuments")
    public ApiResponse<ListData<DocumentDto>> listDocuments(
            @PathVariable String projectId,
            @RequestParam(name = "type",         required = false) DocumentType type,
            @RequestParam(name = "parse_status", required = false) DocumentParseStatus parseStatus) {

        ListData<DocumentDto> data = documentService.listDocuments(projectId, type, parseStatus);
        return ApiResponse.ok(data, MetaDto.of(1, (int) data.getTotalCount(), data.getTotalCount()));
    }

    /**
     * POST /projects/{projectId}/documents  (multipart/form-data)
     * operationId: uploadDocument
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "문서 업로드", operationId = "uploadDocument")
    public ApiResponse<DocumentDto> uploadDocument(
            @PathVariable String projectId,
            @ModelAttribute @Valid DocumentUploadRequest request) {

        return ApiResponse.ok(documentService.uploadDocument(projectId, request));
    }

    /**
     * GET /projects/{projectId}/documents/{documentId}
     * operationId: getDocument
     */
    @GetMapping("/{documentId}")
    @Operation(summary = "문서 상세 조회", operationId = "getDocument")
    public ApiResponse<DocumentDto> getDocument(
            @PathVariable String projectId,
            @PathVariable String documentId) {

        return ApiResponse.ok(documentService.getDocument(projectId, documentId));
    }

    /**
     * DELETE /projects/{projectId}/documents/{documentId}
     * operationId: deleteDocument
     */
    @DeleteMapping("/{documentId}")
    @Operation(summary = "문서 삭제", operationId = "deleteDocument")
    public ApiResponse<Void> deleteDocument(
            @PathVariable String projectId,
            @PathVariable String documentId) {

        documentService.deleteDocument(projectId, documentId);
        return ApiResponse.ok();
    }

    /**
     * GET /projects/{projectId}/documents/{documentId}/versions
     * operationId: listDocumentVersions
     */
    @GetMapping("/{documentId}/versions")
    @Operation(summary = "문서 버전 목록 조회", operationId = "listDocumentVersions")
    public ApiResponse<ListData<DocumentDto>> listVersions(
            @PathVariable String projectId,
            @PathVariable String documentId) {

        List<DocumentDto> items = documentService.listVersions(projectId, documentId);
        return ApiResponse.ok(new ListData<>(items, items.size()));
    }
}
