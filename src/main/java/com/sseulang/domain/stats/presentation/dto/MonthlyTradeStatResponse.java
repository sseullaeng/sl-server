package com.sseulang.domain.stats.presentation.dto;

import com.sseulang.domain.transaction.domain.TransactionMonthlyStat;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 월별 거래완료 집계 응답. recharts 차트 친화 (month ASC, 빈 월은 count=0/amount=0 으로 채워짐).
 */
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
