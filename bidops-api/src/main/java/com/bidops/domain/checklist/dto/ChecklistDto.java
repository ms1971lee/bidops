package com.bidops.domain.checklist.dto;

import com.bidops.domain.checklist.entity.SubmissionChecklist;
import com.bidops.domain.checklist.enums.ChecklistType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChecklistDto {

    private String id;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("checklist_type")
    private ChecklistType checklistType;

    private String title;

    @JsonProperty("total_count")
    private long totalCount;

    @JsonProperty("done_count")
    private long doneCount;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static ChecklistDto from(SubmissionChecklist c, long totalCount, long doneCount) {
        return ChecklistDto.builder()
                .id(c.getId())
                .projectId(c.getProjectId())
                .checklistType(c.getChecklistType())
                .title(c.getTitle())
                .totalCount(totalCount)
                .doneCount(doneCount)
                .createdAt(c.getCreatedAt())
                .build();
    }
}
