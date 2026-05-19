package com.sseulang.domain.stats.presentation.dto;

import com.sseulang.domain.transaction.domain.TransactionMonthlyStat;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "월별 거래완료 집계.")
public record MonthlyTradeStatResponse(
        @Schema(example = "2026-05") String month,
        @Schema(example = "42") long count,
        @Schema(example = "12500000") long amount
) {
    public static MonthlyTradeStatResponse from(TransactionMonthlyStat r) {
        return new MonthlyTradeStatResponse(r.month().toString(), r.count(), r.amount());
    }
}
