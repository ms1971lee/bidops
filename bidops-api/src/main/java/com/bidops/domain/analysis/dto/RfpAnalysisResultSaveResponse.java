package com.bidops.domain.analysis.dto;

import com.bidops.domain.analysis.dto.RfpAnalysisValidationResponse.ItemWarning;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * RFP 분석 결과 저장 응답.
 */
@Getter
@Builder
public class RfpAnalysisResultSaveResponse {

    private final int savedCount;
    private final int skippedCount;
    private final List<String> requirementIds;
    private final List<ItemWarning> warnings;
}
