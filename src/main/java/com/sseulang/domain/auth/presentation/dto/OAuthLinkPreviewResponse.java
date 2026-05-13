package com.sseulang.domain.auth.presentation.dto;

import com.sseulang.domain.user.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth 연결 사전 조회 응답 — 이메일 일치 확인 + 5분짜리 linkKey.")
public record OAuthLinkPreviewResponse(
        @Schema(description = "확정 단계에서 사용할 1회용 키", example = "550e8400-e29b-41d4-a716-446655440000")
        String linkKey,
        @Schema(description = "연결 대상 provider", example = "KAKAO")
        SocialProvider provider,
        @Schema(description = "provider 측 이메일 (현재 로그인 이메일과 동일 확인됨)")
        String providerEmail,
        @Schema(description = "키 만료 시간(초)", example = "300")
        int expiresInSeconds
) {
}
