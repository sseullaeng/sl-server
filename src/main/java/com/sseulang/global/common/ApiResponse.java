package com.sseulang.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(String code, String message, String traceId) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, traceId));
    }

    public record ErrorBody(String code, String message, String traceId) {}
}
