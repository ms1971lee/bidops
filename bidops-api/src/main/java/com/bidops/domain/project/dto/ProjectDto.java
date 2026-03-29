package com.bidops.domain.project.dto;

import com.bidops.domain.project.entity.Project;
import com.bidops.domain.project.enums.ProjectStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/** openapi.yaml Project schema */
@Getter
@Builder
public class ProjectDto {

    private String id;

    @JsonProperty("organization_id")
    private String organizationId;

    private String name;

    @JsonProperty("client_name")
    private String clientName;

    @JsonProperty("business_name")
    private String businessName;

    private ProjectStatus status;
    private String description;

    @JsonProperty("created_by")
    private String createdBy;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static ProjectDto from(Project p) {
        return ProjectDto.builder()
                .id(p.getId())
                .organizationId(p.getOrganizationId())
                .name(p.getName())
                .clientName(p.getClientName())
                .businessName(p.getBusinessName())
                .status(p.getStatus())
                .description(p.getDescription())
                .createdBy(p.getCreatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
