package com.bidops.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * openapi.yaml 응답 envelope: { success, data, meta, error }
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final MetaDto meta;
    private final Object error;  // 성공 시 null

    private ApiResponse(boolean success, T data, MetaDto meta, Object error) {
        this.success = success;
        this.data = data;
        this.meta = meta;
        this.error = error;
    }

    /** 단건 성공 */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, MetaDto.empty(), null);
    }

    /** 목록 성공 (페이지 메타 포함) */
    public static <T> ApiResponse<T> ok(T data, MetaDto meta) {
        return new ApiResponse<>(true, data, meta, null);
    }

    /** 삭제/상태변경 등 data 없는 성공 */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, MetaDto.empty(), null);
    }

    /** 실패 */
    public static ApiResponse<Void> fail(String code, String message) {
        ErrorDto err = new ErrorDto(code, message);
        return new ApiResponse<>(false, null, MetaDto.empty(), err);
    }

    @Getter
    public static class ErrorDto {
        private final String code;
        private final String message;
        ErrorDto(String code, String message) { this.code = code; this.message = message; }
    }
}
