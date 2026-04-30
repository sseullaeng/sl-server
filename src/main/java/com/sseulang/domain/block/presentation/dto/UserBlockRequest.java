package com.sseulang.domain.block.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "사용자 차단 — 차단 대상의 userId. 차단된 사용자와는 채팅/거래 불가.")
public record UserBlockRequest(
        @Schema(description = "차단할 사용자 id", example = "42")
        @NotNull @Positive Long userId
) { }
