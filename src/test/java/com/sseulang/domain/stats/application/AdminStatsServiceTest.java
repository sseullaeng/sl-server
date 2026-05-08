package com.sseulang.domain.stats.application;

import com.sseulang.domain.delivery.application.DeliveryApplicationService;
import com.sseulang.domain.delivery.application.dto.DeliveryStatsResult;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import com.sseulang.domain.payment.application.PaymentApplicationService;
import com.sseulang.domain.payment.application.dto.PaymentStatsResult;
import com.sseulang.domain.transaction.application.TransactionApplicationService;
import com.sseulang.domain.transaction.application.dto.TransactionStatsResult;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.user.application.dto.UserStatsResult;
import com.sseulang.domain.withdrawal.application.WithdrawalApplicationService;
import com.sseulang.domain.withdrawal.application.dto.WithdrawalStatsResult;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminStatsService 단위 — 5개 도메인 ApplicationService 호출 + 응답 합성을 검증.
 * 각 도메인의 stats 로직은 자체 ApplicationServiceTest 가 책임. 본 테스트는 orchestration 정확성만.
 */
class AdminStatsServiceTest {

    private UserApplicationService userService;
    private TransactionApplicationService transactionService;
    private PaymentApplicationService paymentService;
    private WithdrawalApplicationService withdrawalService;
    private DeliveryApplicationService deliveryService;
    private AdminStatsService statsService;

    @BeforeEach
    void setUp() {
        userService = mock(UserApplicationService.class);
        transactionService = mock(TransactionApplicationService.class);
        paymentService = mock(PaymentApplicationService.class);
        withdrawalService = mock(WithdrawalApplicationService.class);
        deliveryService = mock(DeliveryApplicationService.class);
        var userReportService = mock(com.sseulang.domain.report.application.UserReportApplicationService.class);
        statsService = new AdminStatsService(
                userService, transactionService, paymentService, withdrawalService, deliveryService,
                userReportService,
                java.time.Clock.systemDefaultZone());
    }

    @Test
    @DisplayName("dashboard_5개 도메인 stats 합성 + 각 service 1회만 호출")
    void dashboard_orchestration() {
        UserStatsResult users = new UserStatsResult(100, 5, 2, 93);
        TransactionStatsResult transactions = new TransactionStatsResult(50,
                java.util.Map.of(TransactionStatus.채팅중, 10L, TransactionStatus.예약, 5L,
                        TransactionStatus.거래완료, 30L, TransactionStatus.취소, 5L));
        PaymentStatsResult payments = new PaymentStatsResult(20, 1_000_000L);
        WithdrawalStatsResult withdrawals = new WithdrawalStatsResult(10,
                java.util.Map.of(WithdrawalStatus.신청, 3L, WithdrawalStatus.완료, 7L), 700_000L);
        DeliveryStatsResult deliveries = new DeliveryStatsResult(8,
                java.util.Map.of(DeliveryStatus.모집중, 1L, DeliveryStatus.수락, 0L,
                        DeliveryStatus.배송중, 1L, DeliveryStatus.배송완료, 1L,
                        DeliveryStatus.정산완료, 4L, DeliveryStatus.취소, 1L), 24_000L);

        when(userService.adminGetStats()).thenReturn(users);
        when(transactionService.adminGetStats()).thenReturn(transactions);
        when(paymentService.adminGetStats()).thenReturn(payments);
        when(withdrawalService.adminGetStats()).thenReturn(withdrawals);
        when(deliveryService.adminGetStats()).thenReturn(deliveries);

        AdminDashboardResult result = statsService.dashboard();

        assertThat(result.users()).isSameAs(users);
        assertThat(result.transactions()).isSameAs(transactions);
        assertThat(result.payments()).isSameAs(payments);
        assertThat(result.withdrawals()).isSameAs(withdrawals);
        assertThat(result.deliveries()).isSameAs(deliveries);

        // N+1 회피 — 각 도메인 ApplicationService 는 정확히 1회만 호출되어야 함
        verify(userService).adminGetStats();
        verify(transactionService).adminGetStats();
        verify(paymentService).adminGetStats();
        verify(withdrawalService).adminGetStats();
        verify(deliveryService).adminGetStats();
    }
}
