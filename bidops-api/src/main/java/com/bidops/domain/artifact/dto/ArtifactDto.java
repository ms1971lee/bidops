package com.bidops.domain.artifact.dto;

import com.bidops.domain.artifact.entity.Artifact;
import com.bidops.domain.artifact.enums.ArtifactStatus;
import com.bidops.domain.artifact.enums.AssetType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ArtifactDto {

    private String id;

    @JsonProperty("project_id")
    private String projectId;

    private String title;

    @JsonProperty("asset_type")
    private AssetType assetType;

    private ArtifactStatus status;

    private String description;

    @JsonProperty("linked_requirement_id")
    private String linkedRequirementId;

    @JsonProperty("linked_checklist_item_id")
    private String linkedChecklistItemId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static ArtifactDto from(Artifact a) {
        return ArtifactDto.builder()
                .id(a.getId())
                .projectId(a.getProjectId())
                .title(a.getTitle())
                .assetType(a.getAssetType())
                .status(a.getStatus())
                .description(a.getDescription())
                .linkedRequirementId(a.getLinkedRequirementId())
                .linkedChecklistItemId(a.getLinkedChecklistItemId())
                .createdAt(a.getCreatedAt())
                .createdBy(a.getCreatedBy())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
