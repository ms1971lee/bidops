package com.bidops.domain.document.enums;

/**
 * openapi.yaml DocumentType enum 기준으로 통일.
 *
 * ── 주의 ──────────────────────────────────────────────────────────────────────
 * TODO: ARCHITECTURE.md 에는 DocumentType 값이 별도 명시되어 있지 않으나,
 *       향후 "부속서", "계약서", "규격서" 등을 추가해야 할 경우
 *       openapi.yaml 스펙을 먼저 수정한 뒤 이 Enum을 갱신할 것.
 *       임의로 값을 추가하면 AI 워커 분류 파이프라인과 불일치가 발생한다.
 * TODO: QNA는 발주처 공식 질의응답 문서 전용이다.
 *       내부 질의(QueryItem)와 혼동하지 말 것.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public enum DocumentType {
    /** 제안요청서 본문 */
    RFP,
    /** 부속서·별첨 */
    ANNEX,
    /** 제출 양식 */
    FORM,
    /** 발주처 공식 질의응답 */
    QNA,
    /** 참고용 과거 제안서 */
    PROPOSAL_REFERENCE
}
