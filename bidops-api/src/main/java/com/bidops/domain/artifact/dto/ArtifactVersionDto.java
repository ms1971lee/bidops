package com.bidops.domain.artifact.dto;

import com.bidops.domain.artifact.entity.ArtifactVersion;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ArtifactVersionDto {

    private String id;
    private Integer version;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("version_note")
    private String versionNote;

    @JsonProperty("uploaded_by")
    private String uploadedBy;

    @JsonProperty("viewer_url")
    private String viewerUrl;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static ArtifactVersionDto from(ArtifactVersion v) {
        return from(v, null);
    }

    public static ArtifactVersionDto from(ArtifactVersion v, String viewerUrl) {
        return ArtifactVersionDto.builder()
                .id(v.getId())
                .version(v.getVersion())
                .fileName(v.getFileName())
                .versionNote(v.getVersionNote())
                .uploadedBy(v.getUploadedBy())
                .viewerUrl(viewerUrl)
                .createdAt(v.getCreatedAt())
                .build();
    }
}
