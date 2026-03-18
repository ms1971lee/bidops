package com.bidops.domain.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** POST /projects */
@Getter
@NoArgsConstructor
public class ProjectCreateRequest {

    @NotBlank(message = "name은 필수입니다.")
    private String name;

    @JsonProperty("client_name")
    @NotBlank(message = "client_name은 필수입니다.")
    private String clientName;

    @JsonProperty("business_name")
    @NotBlank(message = "business_name은 필수입니다.")
    private String businessName;

    private String description;
}
