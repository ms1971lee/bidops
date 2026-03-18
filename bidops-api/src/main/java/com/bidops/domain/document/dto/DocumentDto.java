package com.bidops.domain.document.dto;

import com.bidops.domain.document.entity.Document;
import com.bidops.domain.document.enums.DocumentParseStatus;
import com.bidops.domain.document.enums.DocumentType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** openapi.yaml Document schema */
@Getter
@Builder
public class DocumentDto {

    private String id;

    @JsonProperty("project_id")
    private String projectId;

    private DocumentType type;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("storage_path")
    private String storagePath;

    private Integer version;

    @JsonProperty("version_note")
    private String versionNote;

    @JsonProperty("parse_status")
    private DocumentParseStatus parseStatus;

    @JsonProperty("uploaded_by")
    private String uploadedBy;

    @JsonProperty("uploaded_at")
    private LocalDateTime uploadedAt;

    public static DocumentDto from(Document d) {
        return DocumentDto.builder()
                .id(d.getId())
                .projectId(d.getProjectId())
                .type(d.getType())
                .fileName(d.getFileName())
                .storagePath(d.getStoragePath())
                .version(d.getVersion())
                .versionNote(d.getVersionNote())
                .parseStatus(d.getParseStatus())
                .uploadedBy(d.getUploadedBy())
                .uploadedAt(d.getCreatedAt())
                .build();
    }
}
