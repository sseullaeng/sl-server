package com.sseulang.domain.overdue.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "본인 누적 연체 채무 잔액. 다음 충전 시 우선 차감.")
public record UserOverdueDebtResponse(
        @Schema(example = "23000", description = "현재 누적 채무 (원). 0 이면 채무 없음.")
        long debt
) { }
