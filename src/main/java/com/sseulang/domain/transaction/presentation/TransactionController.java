package com.sseulang.domain.transaction.presentation;

import com.sseulang.domain.transaction.application.TransactionApplicationService;
import com.sseulang.domain.transaction.presentation.dto.TransactionCreateRequest;
import com.sseulang.domain.transaction.presentation.dto.TransactionIdResponse;
import com.sseulang.domain.transaction.presentation.dto.TransactionPatchRequest;
import com.sseulang.domain.transaction.presentation.dto.TransactionResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transaction", description = "거래 — 채팅중→예약→인계완료→거래완료 (또는 취소). "
        + "라운드 11 escrow hold: 예약 시 buyer 잔액 hold, 인수확인 시 seller 정산.")
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionApplicationService transactionService;

    public TransactionController(TransactionApplicationService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "거래 생성 (buyer)",
            description = "이메일 인증 필수. 본인 물품 / 비활성(예약/삭제) 물품 거부. 생성 즉시 status=채팅중.")
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionIdResponse>> create(
            @AuthenticationPrincipal Long buyerId,
            @Valid @RequestBody TransactionCreateRequest request
    ) {
        Long id = transactionService.create(request.toCommand(buyerId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new TransactionIdResponse(id)));
    }

    @Operation(summary = "거래 단건 조회",
            description = "거래 참여자(seller/buyer) 만 조회 가능. 그 외 403 TRANSACTION_FORBIDDEN.")
    @GetMapping("/{id}")
    public ApiResponse<TransactionResponse> getOne(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(TransactionResponse.from(transactionService.getById(id, requesterId)));
    }

    /**
     * 거래 상태 전이. action 별 분기 (라운드 11):
     * <ul>
     *   <li>{@code 예약}: seller. Item 비관적 락 + buyer escrow hold (잔액 부족 시 INSUFFICIENT_POINT).</li>
     *   <li>{@code 인계확인}: seller. 예약 → 인계완료. 잔액 변동 X. 멱등.</li>
     *   <li>{@code 인수확인}: buyer. 인계완료 → 거래완료 자동 전이 + 정산 (buyer hold 해제 + seller credit). 멱등.</li>
     *   <li>{@code 취소}: 양쪽 참여자. 채팅중/예약 단계만 (인계완료 이후 차단 — R2 분쟁). 예약 단계 취소 시 Item 복원 + escrowRefund.</li>
     * </ul>
     */
    @Operation(summary = "거래 상태 전이 (예약 / 인계확인 / 인수확인 / 취소)",
            description = "action 분기 — 예약: seller(잔액 부족 시 INSUFFICIENT_POINT), 인계확인: seller, "
                    + "인수확인: buyer(자동 거래완료 + 정산), 취소: 양쪽(채팅중/예약 단계만). "
                    + "동시 reserve 는 한 건만 성공(409 TRANSACTION_RESERVED_BY_OTHER).")
    @PatchMapping("/{id}")
    public ApiResponse<Void> patch(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id,
            @Valid @RequestBody TransactionPatchRequest request
    ) {
        if (request.isReserve()) {
            transactionService.reserve(id, requesterId);
        } else if (request.isHandover()) {
            transactionService.markHandover(id, requesterId);
        } else if (request.isReceive()) {
            transactionService.markReceived(id, requesterId);
        } else if (request.isCancel()) {
            transactionService.cancel(id, requesterId, request.cancelReason());
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return ApiResponse.ok();
    }
}
