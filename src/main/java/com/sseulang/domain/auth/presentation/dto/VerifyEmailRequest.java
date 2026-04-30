package com.sseulang.domain.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 인증 토큰 검증. 가입 메일에 포함된 32~64자 토큰.")
public record VerifyEmailRequest(
        @Schema(
                description = "가입 메일에 포함된 인증 토큰 (UUID hex 32자)",
                example = "a1b2c3d4e5f60718293a4b5c6d7e8f90",
                minLength = 32, maxLength = 64
        )
        @NotBlank @Size(min = 32, max = 64) String token
) {}
