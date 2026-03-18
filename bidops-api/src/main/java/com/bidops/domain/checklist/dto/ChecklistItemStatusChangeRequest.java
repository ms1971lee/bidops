package com.bidops.domain.checklist.dto;

import com.bidops.domain.checklist.enums.ChecklistItemStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChecklistItemStatusChangeRequest {

    @NotNull(message = "status는 필수입니다")
    @JsonProperty("status")
    private ChecklistItemStatus status;
}
