package com.bidops.domain.checklist.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChecklistGenerateResponse {

    @JsonProperty("checklist_id")
    private final String checklistId;

    @JsonProperty("created_count")
    private final int createdCount;

    @JsonProperty("skipped_count")
    private final int skippedCount;

    @JsonProperty("created_item_ids")
    private final List<String> createdItemIds;
}
