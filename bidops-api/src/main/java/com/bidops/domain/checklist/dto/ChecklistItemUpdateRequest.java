package com.bidops.domain.checklist.dto;

import com.bidops.domain.checklist.enums.RiskLevel;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChecklistItemUpdateRequest {

    @JsonProperty("item_text")
    private String itemText;

    @JsonProperty("mandatory_flag")
    private Boolean mandatoryFlag;

    @JsonProperty("due_hint")
    private String dueHint;

    @JsonProperty("risk_level")
    private RiskLevel riskLevel;

    @JsonProperty("risk_note")
    private String riskNote;

    @JsonProperty("linked_requirement_id")
    private String linkedRequirementId;

    @JsonProperty("owner_user_id")
    private String ownerUserId;

    @JsonProperty("action_comment")
    private String actionComment;
}
