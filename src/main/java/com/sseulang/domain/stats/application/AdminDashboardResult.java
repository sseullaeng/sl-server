package com.sseulang.domain.stats.application;

import com.sseulang.domain.delivery.application.dto.DeliveryStatsResult;
import com.sseulang.domain.payment.application.dto.PaymentStatsResult;
import com.sseulang.domain.transaction.application.dto.TransactionStatsResult;
import com.sseulang.domain.user.application.dto.UserStatsResult;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalStatsResult;

public record AdminDashboardResult(
        UserStatsResult users,
        TransactionStatsResult transactions,
        PaymentStatsResult payments,
        WithdrawalStatsResult withdrawals,
        DeliveryStatsResult deliveries
) {}
