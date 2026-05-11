package com.sseulang.domain.stats.presentation.dto;

import com.sseulang.domain.delivery.application.dto.DeliveryStatsResult;
import com.sseulang.domain.payment.application.dto.PaymentStatsResult;
import com.sseulang.domain.stats.application.AdminDashboardResult;
import com.sseulang.domain.transaction.application.dto.TransactionStatsResult;
import com.sseulang.domain.user.application.dto.UserStatsResult;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalStatsResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 dashboard — 5개 도메인 (User/Transaction/Payment/Withdrawal/Delivery) 통계 묶음.")
public record AdminDashboardResponse(
        UserStatsResult users,
        TransactionStatsResult transactions,
        PaymentStatsResult payments,
        WithdrawalStatsResult withdrawals,
        DeliveryStatsResult deliveries
) {
    public static AdminDashboardResponse from(AdminDashboardResult r) {
        return new AdminDashboardResponse(
                r.users(), r.transactions(), r.payments(), r.withdrawals(), r.deliveries()
        );
    }
}
