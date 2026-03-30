package com.bidops.domain.requirement.enums;

/**
 * 품질 검증 이슈 코드.
 * severity: CRITICAL(치명) 또는 MINOR(일반)
 * message: 사용자 표시 문구
 */
public enum QualityIssueCode {

    // ── 치명 이슈 ──
    QUERY_CLARIFICATION_MISSING("CRITICAL", "질의 필요 항목인데 질의 내용이 없습니다."),
    DELIVERABLES_EMPTY("CRITICAL", "예상 산출물이 없습니다."),
    RISK_NOTE_EMPTY("CRITICAL", "리스크 분석이 없습니다."),
    IMPLEMENTATION_REPEATS_ORIGINAL("CRITICAL", "구현 방향이 원문을 반복하고 있습니다."),
    PROPOSAL_REPEATS_ORIGINAL("CRITICAL", "제안 포인트가 원문을 반복하고 있습니다."),

    // ── 일반 이슈 ──
    FACT_SUMMARY_WEAK("MINOR", "원문 근거 요약이 부족합니다."),
    INTERPRETATION_WEAK("MINOR", "발주처 의도 해석이 부족합니다."),
    INTENT_SUMMARY_WEAK("MINOR", "발주처 의도 요약이 부족합니다."),
    PROPOSAL_POINT_WEAK("MINOR", "제안 포인트가 부족합니다."),
    IMPLEMENTATION_WEAK("MINOR", "구현 방향이 부족합니다."),
    DIFFERENTIATION_WEAK("MINOR", "차별화 포인트가 부족합니다."),
    EVALUATION_FOCUS_WEAK("MINOR", "평가 중점 사항이 부족합니다."),
    DRAFT_SNIPPET_WEAK("MINOR", "제안서 초안이 부족합니다."),
    DELIVERABLES_INSUFFICIENT("MINOR", "예상 산출물이 2건 미만입니다.");

    private final String severity;
    private final String message;

    QualityIssueCode(String severity, String message) {
        this.severity = severity;
        this.message = message;
    }

    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public boolean isCritical() { return "CRITICAL".equals(severity); }
}
