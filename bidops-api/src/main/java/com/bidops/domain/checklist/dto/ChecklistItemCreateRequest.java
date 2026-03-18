package com.bidops.domain.checklist.dto;

import com.bidops.domain.checklist.enums.RiskLevel;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChecklistItemCreateRequest {

    @NotBlank(message = "item_text는 필수입니다")
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

    @JsonProperty("source_excerpt_id")
    private String sourceExcerptId;
}
