package com.sseulang.domain.point.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 본인 포인트 잔액 응답 — 라운드 11 (B-1 A안 합의). 잔액 페이지에서 3분할 표시:
 * "{balance}P 사용 가능 · {holdAmount}P 거래 보관 중 · 총 {totalBalance}P".
 */
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
