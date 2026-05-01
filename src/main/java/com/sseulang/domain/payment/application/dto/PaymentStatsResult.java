package com.sseulang.domain.payment.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 결제 통계 — 완료된 결제 건수 + 누적 금액. 미완료/실패/환불 결제는 제외.
 */
@Schema(description = "관리자 dashboard — 결제 통계 (완료된 결제만).")
public record PaymentStatsResult(
        @Schema(example = "342", description = "완료(PAID) 상태 결제 수") long paidCount,
        @Schema(example = "12500000", description = "완료된 결제 누적 금액 합계 (KRW)") long totalPaidAmount
) {}
