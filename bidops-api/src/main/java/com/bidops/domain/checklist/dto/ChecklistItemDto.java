package com.bidops.domain.checklist.dto;

import com.bidops.domain.checklist.entity.ChecklistItem;
import com.bidops.domain.checklist.enums.ChecklistItemStatus;
import com.bidops.domain.checklist.enums.RiskLevel;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChecklistItemDto {

    private String id;

    @JsonProperty("checklist_id")
    private String checklistId;

    @JsonProperty("item_code")
    private String itemCode;

    @JsonProperty("item_text")
    private String itemText;

    @JsonProperty("mandatory_flag")
    private boolean mandatoryFlag;

    @JsonProperty("due_hint")
    private String dueHint;

    @JsonProperty("current_status")
    private ChecklistItemStatus currentStatus;

    @JsonProperty("risk_level")
    private RiskLevel riskLevel;

    @JsonProperty("risk_note")
    private String riskNote;

    @JsonProperty("linked_requirement_id")
    private String linkedRequirementId;

    @JsonProperty("source_excerpt_id")
    private String sourceExcerptId;

    @JsonProperty("owner_user_id")
    private String ownerUserId;

    @JsonProperty("action_comment")
    private String actionComment;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static ChecklistItemDto from(ChecklistItem i) {
        return ChecklistItemDto.builder()
                .id(i.getId())
                .checklistId(i.getChecklistId())
                .itemCode(i.getItemCode())
                .itemText(i.getItemText())
                .mandatoryFlag(i.isMandatoryFlag())
                .dueHint(i.getDueHint())
                .currentStatus(i.getCurrentStatus())
                .riskLevel(i.getRiskLevel())
                .riskNote(i.getRiskNote())
                .linkedRequirementId(i.getLinkedRequirementId())
                .sourceExcerptId(i.getSourceExcerptId())
                .ownerUserId(i.getOwnerUserId())
                .actionComment(i.getActionComment())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}
