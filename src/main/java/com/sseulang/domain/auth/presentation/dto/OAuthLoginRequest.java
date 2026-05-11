package com.sseulang.domain.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "OAuth Authorization Code Grant — 프론트가 redirect 로 받은 code + redirectUri 전달.")
public record OAuthLoginRequest(
        @Schema(
                description = "카카오/구글 OAuth redirect 의 code 파라미터",
                example = "abcDEF12345..."
        )
        @NotBlank(message = "code 는 필수입니다")
        String code,

        @Schema(
                description = "프론트가 SDK init 시 등록한 redirect URI — 카카오/구글 token endpoint 가 일치 여부 검증",
                example = "http://localhost:3000/auth/kakao/callback"
        )
        @NotBlank(message = "redirectUri 는 필수입니다")
        String redirectUri
) {
}
