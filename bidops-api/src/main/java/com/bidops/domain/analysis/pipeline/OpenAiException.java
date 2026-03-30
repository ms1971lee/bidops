package com.bidops.domain.analysis.pipeline;

import lombok.Getter;

/**
 * OpenAI 호출 관련 구조화된 예외.
 * errorCode로 재시도 가능 여부를 판단하고,
 * userMessage는 AnalysisJob.errorMessage에, providerDetail은 로그에 기록한다.
 */
@Getter
public class OpenAiException extends RuntimeException {

    private final OpenAiErrorCode errorCode;
    private final int httpStatus;
    private final String providerDetail;

    public OpenAiException(OpenAiErrorCode errorCode, String providerDetail) {
        super(errorCode.getUserMessage());
        this.errorCode = errorCode;
        this.httpStatus = 0;
        this.providerDetail = providerDetail;
    }

    public OpenAiException(OpenAiErrorCode errorCode, int httpStatus, String providerDetail) {
        super(errorCode.getUserMessage());
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.providerDetail = providerDetail;
    }

    public OpenAiException(OpenAiErrorCode errorCode, String providerDetail, Throwable cause) {
        super(errorCode.getUserMessage(), cause);
        this.errorCode = errorCode;
        this.httpStatus = 0;
        this.providerDetail = providerDetail;
    }

    public boolean isRetryable() {
        return errorCode.isRetryable();
    }
}
