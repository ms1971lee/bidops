package com.bidops.domain.analysis.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * RFP 분석 결과 항목의 신뢰 수준 및 후속 조치 구분.
 * AI 워커가 반환하는 status 값과 1:1 매핑된다.
 * 허용 값: 확인완료, 원문확인필요, 질의필요, 추정, 파싱한계
 */
public enum AnalysisResultStatus {

    확인완료("확인완료"),
    원문확인필요("원문확인필요"),
    질의필요("질의필요"),
    추정("추정"),
    파싱한계("파싱한계");

    private final String value;

    AnalysisResultStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static AnalysisResultStatus from(String value) {
        for (AnalysisResultStatus s : values()) {
            if (s.value.equals(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException(
                "허용되지 않는 status 값: '" + value + "'. 허용 값: 확인완료, 원문확인필요, 질의필요, 추정, 파싱한계");
    }
}
