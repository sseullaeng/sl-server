package com.sseulang.domain.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "관리자 사용자 차단/해제. blocked=true 면 로그인·거래 모두 차단.")
public record UserBlockRequest(
        @Schema(description = "차단 여부", example = "true")
        @NotNull Boolean blocked
) {}
