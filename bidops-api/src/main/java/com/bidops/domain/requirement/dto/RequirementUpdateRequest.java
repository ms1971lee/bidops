package com.bidops.domain.requirement.dto;

import com.bidops.domain.requirement.enums.RequirementAnalysisStatus;
import com.bidops.domain.requirement.enums.RequirementCategory;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** PATCH /requirements/{requirementId} */
@Getter
@NoArgsConstructor
public class RequirementUpdateRequest {
    private String title;
    private RequirementCategory category;
    @JsonProperty("mandatory_flag")         private Boolean mandatoryFlag;
    @JsonProperty("evidence_required_flag") private Boolean evidenceRequiredFlag;
    @JsonProperty("analysis_status")        private RequirementAnalysisStatus analysisStatus;
}
