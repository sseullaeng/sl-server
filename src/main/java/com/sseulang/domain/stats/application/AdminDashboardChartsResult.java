package com.sseulang.domain.stats.application;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 대시보드 차트 데이터 — recharts 친화 (date ASC, 빈 일자 0 채움).
 *
 * <p>프론트 합의 (옵션 A'):
 * <ul>
 *   <li>summary: 카드 4종 + 비교값</li>
 *   <li>signupTrend: 일자별 가입자 (AreaChart)</li>
 *   <li>tradeByType: TradeType 별 (BarChart) — 한국어 enum</li>
 *   <li>tradeByStatus: 진행중/완료/취소 3분류 (PieChart) — 라운드 11 5단계 그룹핑</li>
 * </ul>
 */
public record AdminDashboardChartsResult(
        Summary summary,
        List<DailyCount> signupTrend,
        List<TradeTypeCount> tradeByType,
        List<StatusGroupCount> tradeByStatus,
        ReportsSummary reportsSummary
) {
    public record Summary(
            UsersSummary users,
            TodaySignupsSummary todaySignups,
            MonthTradesSummary monthTrades,
            long pendingReports
    ) { }

    /**
     * 관리자 대시보드 신고 위젯 (PR-F #9 라운드 12).
     *
     * @param pending        처리 대기 (접수 + 처리중)
     * @param resolved       처리 완료 (처리완료 + 반려)
     * @param totalLast7Days 최근 7일 (생성 기준, 전체 status)
     */
    public record ReportsSummary(long pending, long resolved, long totalLast7Days) { }

    public record UsersSummary(long total, long monthDelta) { }

    public record TodaySignupsSummary(long count, long yesterdayDelta) { }

    /** prevMonthRate: 전월 대비 증감률. 전월 0이면 null. */
    public record MonthTradesSummary(long count, Double prevMonthRate) { }

    public record DailyCount(LocalDate date, long count) { }

    /** tradeType: 한국어 enum (판매/대여/나눔). */
    public record TradeTypeCount(String type, long count) { }

    /** group: 진행중/완료/취소. */
    public record StatusGroupCount(String status, long count) { }
}
