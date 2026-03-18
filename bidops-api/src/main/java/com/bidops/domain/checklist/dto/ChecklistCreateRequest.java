package com.bidops.domain.checklist.dto;

import com.bidops.domain.checklist.enums.ChecklistType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChecklistCreateRequest {

    @NotNull(message = "checklist_type은 필수입니다")
    @JsonProperty("checklist_type")
    private ChecklistType checklistType;

    @NotBlank(message = "title은 필수입니다")
    private String title;
}
