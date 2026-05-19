package com.sseulang.domain.stats.application;

import java.time.LocalDate;
import java.util.List;

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

    

    public record ReportsSummary(long pending, long resolved, long totalLast7Days) { }

    public record UsersSummary(long total, long monthDelta) { }

    public record TodaySignupsSummary(long count, long yesterdayDelta) { }

    
    public record MonthTradesSummary(long count, Double prevMonthRate) { }

    public record DailyCount(LocalDate date, long count) { }

    
    public record TradeTypeCount(String type, long count) { }

    
    public record StatusGroupCount(String status, long count) { }
}
