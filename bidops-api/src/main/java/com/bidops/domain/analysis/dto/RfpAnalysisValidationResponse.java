package com.bidops.domain.analysis.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * RFP 분석 결과 검증 응답.
 * 검증 성공 시 원본 데이터를 그대로 반환하고, 항목별 검증 경고가 있으면 warnings에 포함한다.
 */
@Getter
@Builder
public class RfpAnalysisValidationResponse {

    private final String analysisJobId;
    private final String documentId;
    private final int totalCount;
    private final int validCount;
    private final int warningCount;
    private final List<RfpAnalysisResultItem> results;
    private final List<ItemWarning> warnings;

    @Getter
    @Builder
    public static class ItemWarning {
        private final int index;
        private final String field;
        private final String message;
    }
}
