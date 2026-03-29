package com.bidops.domain.document.controller;

import com.bidops.common.response.ApiResponse;
import com.bidops.domain.document.dto.SourceExcerptDetailDto;
import com.bidops.domain.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * SourceExcerpt 직접 조회 API.
 *
 * 체크리스트 등에서 source_excerpt_id 만으로
 * requirement sources 를 경유하지 않고 근거 데이터를 조회할 때 사용.
 */
@RestController
@RequestMapping("/api/v1/source-excerpts")
@RequiredArgsConstructor
@Tag(name = "SourceExcerpts", description = "원문 발췌 블록 직접 조회 API")
public class SourceExcerptController {

    private final DocumentService documentService;

    @GetMapping("/{id}")
    @Operation(summary = "SourceExcerpt 단건 조회", operationId = "getSourceExcerpt")
    public ApiResponse<SourceExcerptDetailDto> getSourceExcerpt(@PathVariable String id) {
        return ApiResponse.ok(documentService.getSourceExcerpt(id));
    }
}
