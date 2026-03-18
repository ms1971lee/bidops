package com.bidops.common.exception;

import com.bidops.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> DEV_PROFILES = Set.of("local", "dev");

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @ExceptionHandler(BidOpsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBidOps(BidOpsException e) {
        log.warn("[BidOpsException] code={} message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .body(ApiResponse.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.fail("VALIDATION_ERROR", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception e) {
        log.error("[Unhandled]", e);

        String message = DEV_PROFILES.contains(activeProfile)
                ? e.getClass().getSimpleName() + ": " + e.getMessage()
                : "서버 오류가 발생했습니다.";

        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("INTERNAL_ERROR", message));
    }
}
