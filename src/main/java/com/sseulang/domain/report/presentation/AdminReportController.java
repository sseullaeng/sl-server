package com.sseulang.domain.report.presentation;

import com.sseulang.domain.report.application.UserReportApplicationService;
import com.sseulang.domain.report.domain.ReportStatus;
import com.sseulang.domain.report.presentation.dto.AdminReportDecisionRequest;
import com.sseulang.domain.report.presentation.dto.AdminReportResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final UserReportApplicationService reportService;

    public AdminReportController(UserReportApplicationService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminReportResponse>> list(
            @RequestParam(value = "status", required = false) ReportStatus status,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                reportService.adminFindByStatus(status, pageable).map(AdminReportResponse::from)
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminReportResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(AdminReportResponse.from(reportService.adminFindById(id)));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> decide(
            @AuthenticationPrincipal Long adminId,
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminReportDecisionRequest request
    ) {
        switch (request.action()) {
            case MARK_IN_PROGRESS -> reportService.adminMarkInProgress(id, adminId, request.memo());
            case COMPLETE -> reportService.adminComplete(id, adminId, request.memo());
            case REJECT -> reportService.adminReject(id, adminId, request.memo());
        }
        return ApiResponse.ok();
    }
}
