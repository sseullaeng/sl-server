package com.sseulang.domain.report.presentation;

import com.sseulang.domain.report.application.UserReportApplicationService;
import com.sseulang.domain.report.presentation.dto.ReportIdResponse;
import com.sseulang.domain.report.presentation.dto.ReportRequest;
import com.sseulang.global.common.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자/물품 신고 진입점. Item 신고는 가이드 §6.1 의 {@code POST /api/v1/items/{id}/report},
 * User 신고는 별도 {@code POST /api/v1/users/{id}/report}.
 */
@Tag(name = "Report", description = "사용자 신고 등록")
@RestController
public class UserReportController {

    private final UserReportApplicationService reportService;

    public UserReportController(UserReportApplicationService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/api/v1/items/{itemId}/report")
    public ResponseEntity<ApiResponse<ReportIdResponse>> reportItem(
            @AuthenticationPrincipal Long reporterId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody ReportRequest request
    ) {
        Long id = reportService.reportItem(reporterId, itemId, request.reason(), request.detail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new ReportIdResponse(id)));
    }

    @PostMapping("/api/v1/users/{userId}/report")
    public ResponseEntity<ApiResponse<ReportIdResponse>> reportUser(
            @AuthenticationPrincipal Long reporterId,
            @PathVariable("userId") Long reportedUserId,
            @Valid @RequestBody ReportRequest request
    ) {
        Long id = reportService.reportUser(reporterId, reportedUserId, request.reason(), request.detail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new ReportIdResponse(id)));
    }
}
