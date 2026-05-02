package com.sseulang.domain.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 본문 — Authorization Code Grant.
 *
 * <p>프론트는 카카오/구글 OAuth redirect 받은 {@code code} 와 본인이 사용한 {@code redirectUri} 만
 * 그대로 전달. 백엔드가 provider token endpoint 에 client_id/client_secret 동봉해 access_token
 * 으로 교환 + user info 조회. Client Secret 활성화돼도 안전.</p>
 */
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
