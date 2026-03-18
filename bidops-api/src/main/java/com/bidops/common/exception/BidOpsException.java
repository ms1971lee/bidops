package com.bidops.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BidOpsException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BidOpsException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static BidOpsException notFound(String resource) {
        return new BidOpsException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                resource + "을(를) 찾을 수 없습니다.");
    }

    public static BidOpsException badRequest(String message) {
        return new BidOpsException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static BidOpsException conflict(String message) {
        return new BidOpsException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static BidOpsException forbidden() {
        return new BidOpsException(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
    }
}
