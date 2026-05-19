package com.sseulang.domain.withdrawal.presentation;

import com.sseulang.domain.withdrawal.application.WithdrawalApplicationService;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import com.sseulang.domain.withdrawal.presentation.dto.AdminWithdrawalDecisionRequest;
import com.sseulang.domain.withdrawal.presentation.dto.WithdrawalResponse;
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

@Tag(name = "AdminWithdrawal", description = "관리자 — 출금 신청 승인/거부")
@RestController
@RequestMapping("/api/v1/admin/withdrawals")
public class AdminWithdrawalController {

    private final WithdrawalApplicationService withdrawalService;

    public AdminWithdrawalController(WithdrawalApplicationService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @Operation(summary = "[관리자] 출금 신청 목록", description = "status 필터 (신청/승인/완료/거부) + 페이징.")
    @GetMapping
    public ApiResponse<PageResponse<WithdrawalResponse>> list(
            @RequestParam(value = "status", required = false) WithdrawalStatus status,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                withdrawalService.adminFindByStatus(status, pageable).map(WithdrawalResponse::from)
        ));
    }

    @Operation(summary = "[관리자] 출금 처리",
            description = "action: APPROVE(승인) / REJECT(거부, 잔액 자동 환불) / COMPLETE(외부 이체 완료 표시).")
    @PatchMapping("/{id}")
    public ApiResponse<Void> decide(
            @AuthenticationPrincipal Long adminId,
            @PathVariable("id") Long id,
            @Valid @RequestBody AdminWithdrawalDecisionRequest request
    ) {
        switch (request.action()) {
            case APPROVE -> withdrawalService.adminApprove(id, adminId, request.memo());
            case REJECT -> withdrawalService.adminReject(id, adminId, request.memo());
            case COMPLETE -> withdrawalService.adminComplete(id, adminId);
        }
        return ApiResponse.ok();
    }
}
