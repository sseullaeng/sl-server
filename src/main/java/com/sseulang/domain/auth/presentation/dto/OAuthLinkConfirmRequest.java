package com.sseulang.domain.auth.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "OAuth 계정 연결 확정 요청 — preview 단계에서 발급된 linkKey 로 실제 연결 적용.")
public record OAuthLinkConfirmRequest(
        @Schema(description = "preview 응답의 linkKey", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotBlank(message = "linkKey 는 필수입니다")
        String linkKey
) {
}
