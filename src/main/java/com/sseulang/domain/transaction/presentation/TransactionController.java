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

@Tag(name = "Transaction", description = "거래 — 채팅중→예약→거래완료 (또는 취소). 정산은 거래완료 시점에 buyer→seller 포인트 이동.")
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
     * 거래 상태 전이. action 별 분기:
     * <ul>
     *   <li>{@code 예약}: seller 만 호출 가능. Item 비관적 락 + markAsReserved.</li>
     *   <li>{@code 거래완료}: seller 만. (포인트 이동은 Day 8)</li>
     *   <li>{@code 취소}: 양쪽 참여자. 예약 상태였다면 Item 판매중으로 복원.</li>
     * </ul>
     * action={@code 채팅중} 은 거부 — 채팅중은 create 시점에만 부여되는 초기 상태.
     */
    @Operation(summary = "거래 상태 전이 (예약 / 거래완료 / 취소)",
            description = "action 분기 — 예약: seller, 거래완료: seller(잔액 부족 시 INSUFFICIENT_POINT), 취소: 양쪽. "
                    + "예약→취소 시 Item 판매중으로 복원. 동시 reserve 는 한 건만 성공(409 TRANSACTION_RESERVED_BY_OTHER).")
    @PatchMapping("/{id}")
    public ApiResponse<Void> patch(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id,
            @Valid @RequestBody TransactionPatchRequest request
    ) {
        if (request.isReserve()) {
            transactionService.reserve(id, requesterId);
        } else if (request.isComplete()) {
            transactionService.complete(id, requesterId);
        } else if (request.isCancel()) {
            transactionService.cancel(id, requesterId, request.cancelReason());
        } else {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return ApiResponse.ok();
    }
}
