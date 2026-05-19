package com.sseulang.domain.admin.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 로그인. 성공 시 ROLE_ADMIN AT/RT 쿠키 발급.")
public record AdminLoginRequest(
        @Schema(description = "관리자 username", example = "admin")
        @NotBlank String username,

        @Schema(description = "관리자 비밀번호", example = "AdminP@ssw0rd!")
        @NotBlank String password
) {}
