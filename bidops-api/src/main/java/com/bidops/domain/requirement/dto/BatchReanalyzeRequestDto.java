package com.bidops.domain.requirement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class BatchReanalyzeRequestDto {

    @NotEmpty(message = "재분석할 requirement ID가 필요합니다.")
    @Size(max = 50, message = "한 번에 최대 50건까지 요청 가능합니다.")
    @JsonProperty("requirement_ids")
    private List<String> requirementIds;
}
