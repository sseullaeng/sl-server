package com.sseulang.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessException")
class BusinessExceptionTest {

    @Test
    void 생성_ErrorCode만_전달시_defaultMessage가_적용된다() {
        BusinessException exception = new BusinessException(ErrorCode.USER_NOT_FOUND);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.USER_NOT_FOUND.getDefaultMessage());
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void 생성_커스텀메시지_전달시_message가_오버라이드된다() {
        BusinessException exception = new BusinessException(ErrorCode.INVALID_REQUEST, "id는 양수여야 합니다");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(exception.getMessage()).isEqualTo("id는 양수여야 합니다");
    }

    @Test
    void 생성_원인예외_전달시_cause가_보존된다() {
        IllegalStateException cause = new IllegalStateException("DB down");

        BusinessException exception = new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, cause);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getDefaultMessage());
    }
}
