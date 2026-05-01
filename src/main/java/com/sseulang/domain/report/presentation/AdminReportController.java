package com.sseulang.domain.report.presentation;

import com.sseulang.domain.report.application.UserReportApplicationService;
import com.sseulang.domain.report.domain.ReportStatus;
import com.sseulang.domain.report.presentation.dto.AdminReportDecisionRequest;
import com.sseulang.domain.report.presentation.dto.AdminReportResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AdminReport", description = "관리자 — 신고 처리")
@RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {

    private final UserReportApplicationService reportService;

    public AdminReportController(UserReportApplicationService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "[관리자] 신고 목록", description = "status 필터 (PENDING/IN_PROGRESS/COMPLETED/REJECTED) + 페이징.")
    @GetMapping
    public ApiResponse<PageResponse<AdminReportResponse>> list(
            @RequestParam(value = "status", required = false) ReportStatus status,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                reportService.adminFindByStatus(status, pageable).map(AdminReportResponse::from)
        ));
    }

    @Operation(summary = "[관리자] 신고 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<AdminReportResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(AdminReportResponse.from(reportService.adminFindById(id)));
    }

    @Operation(summary = "[관리자] 신고 처리",
            description = "action: MARK_IN_PROGRESS(검토 시작) / COMPLETE(처리 완료) / REJECT(반려).")
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
