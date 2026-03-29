package com.bidops.domain.artifact.controller;

import com.bidops.common.response.ApiResponse;
import com.bidops.domain.artifact.dto.*;
import com.bidops.domain.artifact.enums.ArtifactStatus;
import com.bidops.domain.artifact.service.impl.ArtifactServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/artifacts")
@RequiredArgsConstructor
@Tag(name = "Artifacts", description = "산출물 관리 API")
public class ArtifactController {

    private final ArtifactServiceImpl artifactService;

    @GetMapping
    @Operation(summary = "산출물 목록", operationId = "listArtifacts")
    public ApiResponse<List<ArtifactDto>> list(@PathVariable String projectId) {
        return ApiResponse.ok(artifactService.list(projectId));
    }

    @GetMapping("/{artifactId}")
    @Operation(summary = "산출물 상세", operationId = "getArtifact")
    public ApiResponse<ArtifactDto> get(@PathVariable String projectId, @PathVariable String artifactId) {
        return ApiResponse.ok(artifactService.get(projectId, artifactId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "산출물 생성", operationId = "createArtifact")
    public ApiResponse<ArtifactDto> create(@PathVariable String projectId,
                                            @RequestBody @Valid ArtifactCreateRequest request) {
        return ApiResponse.ok(artifactService.create(projectId, request));
    }

    @PatchMapping("/{artifactId}")
    @Operation(summary = "산출물 수정", operationId = "updateArtifact")
    public ApiResponse<ArtifactDto> update(@PathVariable String projectId,
                                            @PathVariable String artifactId,
                                            @RequestBody ArtifactUpdateRequest request) {
        return ApiResponse.ok(artifactService.update(projectId, artifactId, request));
    }

    @DeleteMapping("/{artifactId}")
    @Operation(summary = "산출물 삭제", operationId = "deleteArtifact")
    public ApiResponse<Void> delete(@PathVariable String projectId, @PathVariable String artifactId) {
        artifactService.delete(projectId, artifactId);
        return ApiResponse.ok();
    }

    @PostMapping("/{artifactId}/status")
    @Operation(summary = "산출물 상태 변경", operationId = "changeArtifactStatus")
    public ApiResponse<ArtifactDto> changeStatus(@PathVariable String projectId,
                                                  @PathVariable String artifactId,
                                                  @RequestBody java.util.Map<String, String> body) {
        return ApiResponse.ok(artifactService.changeStatus(projectId, artifactId,
                ArtifactStatus.valueOf(body.get("status"))));
    }

    @GetMapping("/{artifactId}/versions")
    @Operation(summary = "버전 목록", operationId = "listArtifactVersions")
    public ApiResponse<List<ArtifactVersionDto>> listVersions(@PathVariable String projectId,
                                                               @PathVariable String artifactId) {
        return ApiResponse.ok(artifactService.listVersions(projectId, artifactId));
    }

    @PostMapping(value = "/{artifactId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "버전 업로드", operationId = "uploadArtifactVersion")
    public ApiResponse<ArtifactVersionDto> uploadVersion(@PathVariable String projectId,
                                                          @PathVariable String artifactId,
                                                          @RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "version_note", required = false) String versionNote) {
        return ApiResponse.ok(artifactService.uploadVersion(projectId, artifactId, file, versionNote));
    }
}
