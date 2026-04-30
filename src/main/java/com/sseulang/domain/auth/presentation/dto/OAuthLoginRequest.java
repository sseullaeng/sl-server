package com.sseulang.domain.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 본문.
 *
 * <p>가이드 §4.4 "프론트가 카카오/구글 SDK로 access_token 획득 → 백엔드로 전달".</p>
 */
@Schema(description = "프론트가 SDK로 받은 OAuth access_token 을 전달. 백엔드가 사용자 정보 조회 + 가입/로그인 처리.")
public record OAuthLoginRequest(
        @Schema(
                description = "카카오/구글 SDK 에서 받은 OAuth access_token",
                example = "ya29.a0AfH6SMBxYVhc0BkExampleAccessToken..."
        )
        @NotBlank(message = "accessToken 은 필수입니다")
        String accessToken
) {
}
