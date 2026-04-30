package com.sseulang.domain.stats.presentation;

import com.sseulang.domain.stats.application.AdminStatsService;
import com.sseulang.domain.stats.presentation.dto.AdminDashboardResponse;
import com.sseulang.global.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 통계 dashboard. SecurityConfig admin chain 으로 ROLE_ADMIN 강제. */
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    private final AdminStatsService statsService;

    public AdminStatsController(AdminStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> dashboard() {
        return ApiResponse.ok(AdminDashboardResponse.from(statsService.dashboard()));
    }
}
