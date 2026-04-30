package com.sseulang.domain.transaction.application.dto;

import com.sseulang.domain.transaction.domain.TransactionStatus;

import java.util.Map;

/**
 * 거래 통계 — total + status 별 카운트. byStatus 는 모든 enum 값 포함 (0 이면 0L).
 */
public record TransactionStatsResult(long total, Map<TransactionStatus, Long> byStatus) {}
