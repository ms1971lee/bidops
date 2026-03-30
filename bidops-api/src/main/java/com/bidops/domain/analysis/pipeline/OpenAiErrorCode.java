package com.bidops.domain.analysis.pipeline;

/**
 * OpenAI 호출 관련 표준 에러 코드.
 * retryable: 재시도 대상 여부.
 * userMessage: 사용자에게 표시할 짧은 문구.
 */
public enum OpenAiErrorCode {

    // ── 재시도 대상 (transient) ──
    OPENAI_TIMEOUT(true, "AI 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."),
    OPENAI_RATE_LIMIT(true, "AI 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."),
    OPENAI_SERVER_ERROR(true, "AI 서버에 일시적인 오류가 발생했습니다."),
    OPENAI_CONNECTION_ERROR(true, "AI 서버와의 연결에 실패했습니다."),

    // ── 즉시 실패 (non-retryable) ──
    OPENAI_BAD_REQUEST(false, "AI 요청이 잘못되었습니다."),
    OPENAI_AUTH_ERROR(false, "AI API 인증에 실패했습니다."),
    OPENAI_PARSE_ERROR(false, "AI 응답을 해석할 수 없습니다."),

    // ── 비즈니스 오류 (non-retryable) ──
    REQUIREMENT_NOT_FOUND(false, "요구사항을 찾을 수 없습니다."),
    SOURCE_NOT_FOUND(false, "원문 근거가 없습니다."),
    TARGET_REQUIREMENT_MISSING(false, "재분석 대상이 지정되지 않았습니다."),
    PROMPT_LOAD_ERROR(false, "분석 프롬프트 파일을 불러올 수 없습니다.");

    private final boolean retryable;
    private final String userMessage;

    OpenAiErrorCode(boolean retryable, String userMessage) {
        this.retryable = retryable;
        this.userMessage = userMessage;
    }

    public boolean isRetryable() { return retryable; }
    public String getUserMessage() { return userMessage; }
}
