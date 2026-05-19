package com.sseulang.global.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiResponse")
class ApiResponseTest {

    @Nested
    @DisplayName("ok(data)")
    class Ok {

        @Test
        void 생성_데이터있을때_success_true와_data만_채운다() {
            ApiResponse<String> response = ApiResponse.ok("hello");

            assertThat(response.success()).isTrue();
            assertThat(response.data()).isEqualTo("hello");
            assertThat(response.error()).isNull();
        }

        @Test
        void 생성_데이터없을때_success_true에_data와_error가_null() {
            ApiResponse<Void> response = ApiResponse.ok();

            assertThat(response.success()).isTrue();
            assertThat(response.data()).isNull();
            assertThat(response.error()).isNull();
        }
    }

    @Nested
    @DisplayName("fail(code, message, traceId)")
    class Fail {

        @Test
        void 생성_실패응답_success_false와_error_채운다() {
            ApiResponse<Void> response = ApiResponse.fail("AUTH_LOGIN_FAILED", "로그인 실패", "trace-123");

            assertThat(response.success()).isFalse();
            assertThat(response.data()).isNull();
            assertThat(response.error()).isNotNull();
            assertThat(response.error().code()).isEqualTo("AUTH_LOGIN_FAILED");
            assertThat(response.error().message()).isEqualTo("로그인 실패");
            assertThat(response.error().traceId()).isEqualTo("trace-123");
        }
    }
}
