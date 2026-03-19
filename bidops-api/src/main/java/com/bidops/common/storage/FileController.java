package com.bidops.common.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 저장된 파일을 브라우저에 서빙하는 엔드포인트.
 * GET /api/v1/files/{storagePath} → PDF inline 표시.
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "파일 서빙")
public class FileController {

    private final StorageService storageService;

    @GetMapping("/**")
    @Operation(summary = "파일 다운로드/뷰", operationId = "getFile")
    public ResponseEntity<Resource> getFile(HttpServletRequest request) {
        // /api/v1/files/ 이후 전체 경로 추출
        String path = request.getRequestURI().substring("/api/v1/files/".length());
        Resource resource = storageService.load(path);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }
}
