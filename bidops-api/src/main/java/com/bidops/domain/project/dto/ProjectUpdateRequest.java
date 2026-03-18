package com.bidops.domain.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /projects/{projectId} - 모든 필드 optional */
@Getter
@NoArgsConstructor
public class ProjectUpdateRequest {

    private String name;

    @JsonProperty("client_name")
    private String clientName;

    @JsonProperty("business_name")
    private String businessName;

    private String description;
}
