package com.sseulang.domain.stats.presentation;

import com.sseulang.domain.stats.application.AdminStatsService;
import com.sseulang.domain.stats.presentation.dto.AdminDashboardResponse;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminStats", description = "관리자 — dashboard 통계")
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminStatsService statsService;

    public AdminStatsController(AdminStatsService statsService) {
        this.statsService = statsService;
    }

    @Operation(summary = "관리자 dashboard",
            description = "5개 도메인(User/Transaction/Payment/Withdrawal/Delivery) 집계. ROLE_ADMIN 필수. SQL 11개 단일 호출.")
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.ok(AdminDashboardResponse.from(statsService.dashboard()));
    }

    @Operation(summary = "월별 거래완료 집계",
            description = "completed_at 기준. from/to 형식 'YYYY-MM' (양쪽 inclusive). 거래 0건 월은 0 으로 채워져 차트 친화.")
    @GetMapping("/trades/monthly")
    public ApiResponse<java.util.List<com.sseulang.domain.stats.presentation.dto.MonthlyTradeStatResponse>> tradesMonthly(
            @org.springframework.web.bind.annotation.RequestParam("from")
            @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM") java.time.YearMonth from,
            @org.springframework.web.bind.annotation.RequestParam("to")
            @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM") java.time.YearMonth to
    ) {
        if (from.isAfter(to)) {
            throw new com.sseulang.global.exception.BusinessException(
                    com.sseulang.global.exception.ErrorCode.INVALID_REQUEST);
        }
        return ApiResponse.ok(statsService.tradesMonthly(from, to).stream()
                .map(com.sseulang.domain.stats.presentation.dto.MonthlyTradeStatResponse::from)
                .toList());
    }

    @Operation(summary = "차트 dashboard (recharts 친화)",
            description = "카드 4종 + signupTrend(AreaChart) + tradeByType(BarChart) + tradeByStatus(PieChart). "
                    + "기간 [startDate 00:00, endDate 23:59:59.999) — endDate inclusive. "
                    + "default (양쪽 미지정): 최근 14일 (today-13 ~ today).")
    @GetMapping("/dashboard/charts")
    public ApiResponse<com.sseulang.domain.stats.presentation.dto.AdminDashboardChartsResponse> dashboardCharts(
            @org.springframework.web.bind.annotation.RequestParam(name = "startDate", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate startDate,
            @org.springframework.web.bind.annotation.RequestParam(name = "endDate", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate endDate
    ) {
        return ApiResponse.ok(com.sseulang.domain.stats.presentation.dto.AdminDashboardChartsResponse.from(
                statsService.dashboardCharts(startDate, endDate)
        ));
    }
}
