package com.bidops.domain.requirement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * 품질 검증 이슈 구조화 객체.
 * severity + code + message 구조로 운영 통계/필터/표시 일관성 확보.
 */
@Getter
@Builder
public class QualityIssueDto {

    /** CRITICAL 또는 MINOR */
    private String severity;

    /** 이슈 코드 (운영 통계/필터용). 예: RISK_NOTE_EMPTY */
    private String code;

    /** 사용자 표시 문구 */
    private String message;
}
