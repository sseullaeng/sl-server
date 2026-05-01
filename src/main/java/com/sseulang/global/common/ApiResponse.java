package com.sseulang.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "API 표준 응답 — 성공이면 data, 실패면 error 만 채워진다.")
public record ApiResponse<T>(
        @Schema(description = "성공 여부", example = "true") boolean success,
        @Schema(description = "성공 시 데이터 (실패 시 null)") T data,
        @Schema(description = "실패 시 에러 본문 (성공 시 null)") ErrorBody error
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(String code, String message, String traceId) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, traceId));
    }

    @Schema(description = "에러 본문 — 코드는 ErrorCode enum 값, message 는 한국어, traceId 는 서버 로그 추적용.")
    public record ErrorBody(
            @Schema(description = "ErrorCode enum 값", example = "AUTH_TOKEN_EXPIRED") String code,
            @Schema(description = "한국어 사용자 노출 메시지", example = "만료된 토큰입니다.") String message,
            @Schema(description = "서버 로그 traceId (지원 요청 시 첨부)", example = "9f2c8c5b") String traceId
    ) {}
}
