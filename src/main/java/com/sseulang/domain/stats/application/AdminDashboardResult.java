package com.sseulang.domain.stats.application;

import com.sseulang.domain.payment.application.dto.PaymentStatsResult;
import com.sseulang.domain.transaction.application.dto.TransactionStatsResult;
import com.sseulang.domain.user.application.dto.UserStatsResult;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalStatsResult;

/** 4개 도메인 통계 묶음. 단순 wrapper — 각 도메인 stats result 는 그 도메인 dto 그대로 노출. */
public record AdminDashboardResult(
        UserStatsResult users,
        TransactionStatsResult transactions,
        PaymentStatsResult payments,
        WithdrawalStatsResult withdrawals
) {}
