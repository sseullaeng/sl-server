package com.sseulang.domain.report.presentation;

import com.sseulang.domain.report.application.UserReportApplicationService;
import com.sseulang.domain.report.presentation.dto.ReportIdResponse;
import com.sseulang.domain.report.presentation.dto.ReportRequest;
import com.sseulang.global.common.ApiResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Report", description = "사용자 신고 등록")
@RestController
public class UserReportController {

    private final UserReportApplicationService reportService;

    public UserReportController(UserReportApplicationService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "물품 신고", description = "이메일 인증 필수. 본인 물품 신고도 허용 (자기 보호 케이스).")
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

    @Operation(summary = "사용자 신고", description = "이메일 인증 필수. 신고 후 관리자가 PENDING → 처리.")
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
