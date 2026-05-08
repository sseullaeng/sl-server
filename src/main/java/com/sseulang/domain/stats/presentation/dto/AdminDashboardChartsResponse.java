package com.sseulang.domain.stats.presentation.dto;

import com.sseulang.domain.stats.application.AdminDashboardChartsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "관리자 차트 dashboard — 카드 4종 + 차트 3종 (signupTrend / tradeByType / tradeByStatus).")
public record AdminDashboardChartsResponse(
        Summary summary,
        List<DailyCount> signupTrend,
        List<TradeTypeCount> tradeByType,
        List<StatusGroupCount> tradeByStatus
) {
    public static AdminDashboardChartsResponse from(AdminDashboardChartsResult r) {
        return new AdminDashboardChartsResponse(
                Summary.from(r.summary()),
                r.signupTrend().stream().map(s -> new DailyCount(s.date(), s.count())).toList(),
                r.tradeByType().stream().map(t -> new TradeTypeCount(t.type(), t.count())).toList(),
                r.tradeByStatus().stream().map(s -> new StatusGroupCount(s.status(), s.count())).toList()
        );
    }

    @Schema(description = "요약 카드 4종 + 비교값.")
    public record Summary(
            UsersSummary users,
            TodaySignupsSummary todaySignups,
            MonthTradesSummary monthTrades,
            @Schema(example = "7") long pendingReports
    ) {
        static Summary from(AdminDashboardChartsResult.Summary s) {
            return new Summary(
                    new UsersSummary(s.users().total(), s.users().monthDelta()),
                    new TodaySignupsSummary(s.todaySignups().count(), s.todaySignups().yesterdayDelta()),
                    new MonthTradesSummary(s.monthTrades().count(), s.monthTrades().prevMonthRate()),
                    s.pendingReports()
            );
        }
    }

    public record UsersSummary(
            @Schema(example = "1284") long total,
            @Schema(example = "89", description = "이번 달(1일~today) 신규 가입자 수") long monthDelta
    ) { }

    public record TodaySignupsSummary(
            @Schema(example = "15") long count,
            @Schema(example = "3", description = "오늘 - 어제. 음수 가능") long yesterdayDelta
    ) { }

    public record MonthTradesSummary(
            @Schema(example = "316", description = "이번 달 거래완료 수 (completed_at 기준)") long count,
            @Schema(example = "0.12", description = "(이번달-전월)/전월. 전월 0이면 null", nullable = true) Double prevMonthRate
    ) { }

    public record DailyCount(@Schema(example = "2026-04-23") LocalDate date, @Schema(example = "5") long count) { }

    public record TradeTypeCount(
            @Schema(example = "판매", description = "TradeType — 한국어 enum (판매/대여/나눔)") String type,
            @Schema(example = "120") long count
    ) { }

    public record StatusGroupCount(
            @Schema(example = "진행중", description = "그룹: 진행중/완료/취소 (라운드 11 5단계 → 3 그룹핑)") String status,
            @Schema(example = "56") long count
    ) { }
}
