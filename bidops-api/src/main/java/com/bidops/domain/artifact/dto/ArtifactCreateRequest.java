package com.bidops.domain.artifact.dto;

import com.bidops.domain.artifact.enums.AssetType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ArtifactCreateRequest {

    @NotBlank(message = "title은 필수입니다")
    private String title;

    @NotNull(message = "asset_type은 필수입니다")
    @JsonProperty("asset_type")
    private AssetType assetType;

    private String description;

    @JsonProperty("linked_requirement_id")
    private String linkedRequirementId;

    @JsonProperty("linked_checklist_item_id")
    private String linkedChecklistItemId;
}
