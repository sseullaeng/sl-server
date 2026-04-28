package com.sseulang.domain.auth.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 본문.
 *
 * <p>가이드 §4.4 "프론트가 카카오/구글 SDK로 access_token 획득 → 백엔드로 전달".</p>
 */
public record OAuthLoginRequest(
        @NotBlank(message = "accessToken 은 필수입니다")
        String accessToken
) {
}
