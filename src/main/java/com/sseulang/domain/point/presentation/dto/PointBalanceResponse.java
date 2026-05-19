package com.sseulang.domain.point.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "본인 포인트 잔액 — 사용 가능 / 거래 보관 / 합산 3분할.")
public record PointBalanceResponse(
        @Schema(example = "50000", description = "즉시 사용 가능 (point_balance)") long balance,
        @Schema(example = "10000", description = "진행 중 거래에 hold 된 금액 (point_hold)") long holdAmount,
        @Schema(example = "60000", description = "balance + holdAmount") long totalBalance
) {
    public static PointBalanceResponse of(long balance, long holdAmount) {
        return new PointBalanceResponse(balance, holdAmount, balance + holdAmount);
    }
}
