package com.sseulang.domain.stats.application;

import com.sseulang.domain.delivery.application.DeliveryApplicationService;
import com.sseulang.domain.delivery.application.dto.DeliveryStatsResult;
import com.sseulang.domain.payment.application.PaymentApplicationService;
import com.sseulang.domain.payment.application.dto.PaymentStatsResult;
import com.sseulang.domain.transaction.application.TransactionApplicationService;
import com.sseulang.domain.transaction.application.dto.TransactionStatsResult;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.application.dto.UserStatsResult;
import com.sseulang.domain.withdrawal.application.WithdrawalApplicationService;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalStatsResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 dashboard 통계 — 4개 도메인 ApplicationService 를 호출하여 단순 조립.
 *
 * <p>본 서비스는 자체 상태/도메인 없음 — read-only orchestration. CLAUDE.md §3.3 준수:
 * 다른 도메인 Repository 직접 호출 금지, 각 도메인 ApplicationService 만 의존.</p>
 *
 * <p>각 도메인 stats 호출은 단일 집계 쿼리 (GROUP BY / SUM / COUNT) 라 N+1 없음. 전체 dashboard
 * 한 번 호출 시 SQL 11개 (User 4 [all/blocked/deleted/active] + Transaction 1 + Payment 2 +
 * Withdrawal 2 + Delivery 2). prod 트래픽 스케일 커지면 schedule 캐싱 검토.</p>
 */
@Service
@Transactional(readOnly = true)
public class AdminStatsService {

    private final UserApplicationService userService;
    private final TransactionApplicationService transactionService;
    private final PaymentApplicationService paymentService;
    private final WithdrawalApplicationService withdrawalService;
    private final DeliveryApplicationService deliveryService;

    public AdminStatsService(
            UserApplicationService userService,
            TransactionApplicationService transactionService,
            PaymentApplicationService paymentService,
            WithdrawalApplicationService withdrawalService,
            DeliveryApplicationService deliveryService
    ) {
        this.userService = userService;
        this.transactionService = transactionService;
        this.paymentService = paymentService;
        this.withdrawalService = withdrawalService;
        this.deliveryService = deliveryService;
    }

    public AdminDashboardResult dashboard() {
        UserStatsResult users = userService.adminGetStats();
        TransactionStatsResult transactions = transactionService.adminGetStats();
        PaymentStatsResult payments = paymentService.adminGetStats();
        WithdrawalStatsResult withdrawals = withdrawalService.adminGetStats();
        DeliveryStatsResult deliveries = deliveryService.adminGetStats();
        return new AdminDashboardResult(users, transactions, payments, withdrawals, deliveries);
    }

    /** 월별 거래완료 집계 — recharts 차트용. month ASC, 빈 월은 0 채움. */
    public java.util.List<com.sseulang.domain.transaction.domain.TransactionMonthlyStat> tradesMonthly(
            java.time.YearMonth from, java.time.YearMonth to) {
        return transactionService.adminMonthlyTrades(from, to);
    }
}
