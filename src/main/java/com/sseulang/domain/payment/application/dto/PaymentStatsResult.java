package com.sseulang.domain.payment.application.dto;

/**
 * 결제 통계 — 완료된 결제 건수 + 누적 금액. 미완료/실패/환불 결제는 제외.
 */
public record PaymentStatsResult(long paidCount, long totalPaidAmount) {}
