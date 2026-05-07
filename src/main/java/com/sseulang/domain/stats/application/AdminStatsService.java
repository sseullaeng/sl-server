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
import com.sseulang.domain.report.application.UserReportApplicationService;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.domain.TransactionStatusCount;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import com.sseulang.domain.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
    private final UserReportApplicationService userReportService;
    private final Clock clock;

    public AdminStatsService(
            UserApplicationService userService,
            TransactionApplicationService transactionService,
            PaymentApplicationService paymentService,
            WithdrawalApplicationService withdrawalService,
            DeliveryApplicationService deliveryService,
            UserReportApplicationService userReportService,
            Clock clock
    ) {
        this.userService = userService;
        this.transactionService = transactionService;
        this.paymentService = paymentService;
        this.withdrawalService = withdrawalService;
        this.deliveryService = deliveryService;
        this.userReportService = userReportService;
        this.clock = clock;
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

    /**
     * 차트 dashboard — summary 카드 + signupTrend + tradeByType + tradeByStatus 한 번에.
     * 기간 [startDate 00:00, endDate 23:59:59.999) — endDate 도 inclusive day.
     * default (양쪽 null): 최근 14일 (today-13 ~ today).
     */
    public AdminDashboardChartsResult dashboardCharts(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = (endDate != null) ? endDate : today;
        LocalDate start = (startDate != null) ? startDate : end.minusDays(13);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("startDate 가 endDate 보다 미래일 수 없습니다");
        }

        LocalDateTime fromTs = start.atStartOfDay();
        LocalDateTime toExclusive = end.plusDays(1).atStartOfDay();

        return new AdminDashboardChartsResult(
                buildSummary(today),
                buildSignupTrend(start, end, fromTs, toExclusive),
                buildTradeByType(fromTs, toExclusive),
                buildTradeByStatus(fromTs, toExclusive)
        );
    }

    private AdminDashboardChartsResult.Summary buildSummary(LocalDate today) {
        // users
        long totalUsers = userService.adminGetStats().total();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime tomorrow = today.plusDays(1).atStartOfDay();
        long monthDelta = userService.countSignupsBetween(monthStart, tomorrow);

        // today signups
        LocalDateTime todayStart = today.atStartOfDay();
        long todayCount = userService.countSignupsBetween(todayStart, tomorrow);
        long yesterdayCount = userService.countSignupsBetween(today.minusDays(1).atStartOfDay(), todayStart);
        long yesterdayDelta = todayCount - yesterdayCount;

        // month trades — TransactionMonthlyStat 활용 (status=거래완료, completed_at 기준)
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth prevMonth = thisMonth.minusMonths(1);
        var monthly = transactionService.adminMonthlyTrades(prevMonth, thisMonth);
        long thisMonthTrades = monthly.stream().filter(m -> m.month().equals(thisMonth)).mapToLong(m -> m.count()).sum();
        long prevMonthTrades = monthly.stream().filter(m -> m.month().equals(prevMonth)).mapToLong(m -> m.count()).sum();
        Double prevMonthRate = prevMonthTrades == 0
                ? null
                : (thisMonthTrades - prevMonthTrades) / (double) prevMonthTrades;

        // pending reports
        long pendingReports = userReportService.countPending();

        return new AdminDashboardChartsResult.Summary(
                new AdminDashboardChartsResult.UsersSummary(totalUsers, monthDelta),
                new AdminDashboardChartsResult.TodaySignupsSummary(todayCount, yesterdayDelta),
                new AdminDashboardChartsResult.MonthTradesSummary(thisMonthTrades, prevMonthRate),
                pendingReports
        );
    }

    private List<AdminDashboardChartsResult.DailyCount> buildSignupTrend(
            LocalDate start, LocalDate end, LocalDateTime fromTs, LocalDateTime toExclusive) {
        var raw = userService.findDailySignups(fromTs, toExclusive);
        Map<LocalDate, Long> rawMap = new TreeMap<>();
        for (var r : raw) rawMap.put(r.date(), r.count());
        List<AdminDashboardChartsResult.DailyCount> filled = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            filled.add(new AdminDashboardChartsResult.DailyCount(d, rawMap.getOrDefault(d, 0L)));
        }
        return filled;
    }

    private List<AdminDashboardChartsResult.TradeTypeCount> buildTradeByType(
            LocalDateTime fromTs, LocalDateTime toExclusive) {
        var raw = transactionService.countByTradeTypeBetween(fromTs, toExclusive);
        // 한국어 enum 이름 그대로 (판매/대여/나눔). 빈 type 은 0 채움 — TradeType 모든 값 포함.
        Map<com.sseulang.domain.item.domain.TradeType, Long> rawMap = new EnumMap<>(com.sseulang.domain.item.domain.TradeType.class);
        for (var r : raw) rawMap.put(r.tradeType(), r.count());
        List<AdminDashboardChartsResult.TradeTypeCount> result = new ArrayList<>();
        for (var t : com.sseulang.domain.item.domain.TradeType.values()) {
            result.add(new AdminDashboardChartsResult.TradeTypeCount(t.name(), rawMap.getOrDefault(t, 0L)));
        }
        return result;
    }

    private List<AdminDashboardChartsResult.StatusGroupCount> buildTradeByStatus(
            LocalDateTime fromTs, LocalDateTime toExclusive) {
        var raw = transactionService.countByStatusBetween(fromTs, toExclusive);
        long inProgress = 0, completed = 0, canceled = 0;
        for (TransactionStatusCount r : raw) {
            switch (r.status()) {
                case 채팅중, 예약, 인계완료 -> inProgress += r.count();
                case 거래완료 -> completed += r.count();
                case 취소 -> canceled += r.count();
            }
        }
        return List.of(
                new AdminDashboardChartsResult.StatusGroupCount("진행중", inProgress),
                new AdminDashboardChartsResult.StatusGroupCount("완료", completed),
                new AdminDashboardChartsResult.StatusGroupCount("취소", canceled)
        );
    }
}
