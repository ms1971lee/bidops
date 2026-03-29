package com.bidops.domain.artifact.dto;

import com.bidops.domain.artifact.enums.AssetType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ArtifactUpdateRequest {

    private String title;

    @JsonProperty("asset_type")
    private AssetType assetType;

    private String description;

    @JsonProperty("linked_requirement_id")
    private String linkedRequirementId;

    @JsonProperty("linked_checklist_item_id")
    private String linkedChecklistItemId;
}
