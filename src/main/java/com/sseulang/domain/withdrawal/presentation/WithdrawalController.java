package com.sseulang.domain.withdrawal.presentation;

import com.sseulang.domain.withdrawal.application.WithdrawalApplicationService;
import com.sseulang.domain.withdrawal.presentation.dto.WithdrawalRequest;
import com.sseulang.domain.withdrawal.presentation.dto.WithdrawalResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Withdrawal", description = "포인트 출금 신청 — 신청 시 즉시 잔액 차감 + 관리자 승인 후 외부 이체.")
@RestController
@RequestMapping("/api/v1/withdrawals")
public class WithdrawalController {

    private final WithdrawalApplicationService withdrawalService;

    public WithdrawalController(WithdrawalApplicationService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @Operation(summary = "출금 신청",
            description = "잔액 즉시 차감(원자 UPDATE) + 신청 생성. idempotencyKey 로 중복 차단 — 같은 키 재호출은 동일 신청 반환. "
                    + "잔액 부족 400 INSUFFICIENT_POINT.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> request(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody WithdrawalRequest request
    ) {
        Long id = withdrawalService.request(request.toCommand(userId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(id));
    }

    @Operation(summary = "내 출금 신청 목록",
            description = "본인이 신청한 출금 페이징 (최신순).")
    @GetMapping
    public ApiResponse<PageResponse<WithdrawalResponse>> getMyWithdrawals(
            @AuthenticationPrincipal Long userId,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                withdrawalService.findMyWithdrawals(userId, pageable).map(WithdrawalResponse::from)
        ));
    }

    @Operation(summary = "출금 신청 단건 조회",
            description = "본인 신청만. 그 외 403 WITHDRAWAL_FORBIDDEN.")
    @GetMapping("/{id}")
    public ApiResponse<WithdrawalResponse> getOne(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(WithdrawalResponse.from(withdrawalService.getById(id, userId)));
    }

    @Operation(summary = "출금 신청 취소",
            description = "신청 상태(관리자 미처리)일 때만 가능. 잔액 자동 환불. 승인/완료 후 취소는 400 WITHDRAWAL_NOT_CANCELABLE.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id
    ) {
        withdrawalService.cancel(id, userId);
        return ApiResponse.ok();
    }
}
