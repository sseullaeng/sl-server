package com.sseulang.domain.stats.presentation.dto;

import com.sseulang.domain.delivery.application.dto.DeliveryStatsResult;
import com.sseulang.domain.payment.application.dto.PaymentStatsResult;
import com.sseulang.domain.stats.application.AdminDashboardResult;
import com.sseulang.domain.transaction.application.dto.TransactionStatsResult;
import com.sseulang.domain.user.application.dto.UserStatsResult;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalStatsResult;

/**
 * 관리자 dashboard JSON 응답. presentation layer 응답 — application Result 를 그대로 노출하지만
 * 향후 응답 shape 분리(예: 일부 필드 마스킹)에 대비해 wrapper 유지.
 */
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
