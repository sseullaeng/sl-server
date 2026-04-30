package com.sseulang.domain.withdrawal.application.dto;

import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;

import java.util.Map;

/**
 * 출금 통계 — total + status 별 카운트 + 완료된 출금 누적 금액.
 */
public record WithdrawalStatsResult(
        long total,
        Map<WithdrawalStatus, Long> byStatus,
        long completedAmount
) {}
