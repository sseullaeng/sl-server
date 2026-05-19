package com.sseulang.domain.transaction.presentation;

import com.sseulang.domain.transaction.application.TransactionApplicationService;
import com.sseulang.domain.transaction.presentation.dto.RentalBlockResponse;
import com.sseulang.domain.transaction.presentation.dto.RentalRequestRequest;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Rental", description = "대여 거래 — 달력 비활성화 + buyer 신청")
@RestController
@RequestMapping("/api/v1/items/{itemId}")
public class RentalController {

    private final TransactionApplicationService transactionService;

    public RentalController(TransactionApplicationService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "물품 대여 예약 기간 (달력 비활성화용)",
            description = "활성 대여 거래(취소/거래완료 제외)의 rentalStart/rentalEnd 페어. 비로그인 공개. "
                    + "프론트가 달력에서 해당 기간을 회색 처리.")
    @GetMapping("/rental-blocks")
    public ApiResponse<RentalBlockResponse> rentalBlocks(@PathVariable("itemId") Long itemId) {
        var blocks = transactionService.findRentalBlocks(itemId).stream()
                .map(b -> new RentalBlockResponse.Block(b.start(), b.end()))
                .toList();
        return ApiResponse.ok(new RentalBlockResponse(blocks));
    }

    @Operation(summary = "대여 신청 (buyer)",
            description = "buyer 가 기간 지정해서 신청. status=채팅중 으로 Transaction 생성 → seller 가 [예약]으로 수락. "
                    + "기존 활성 대여와 기간 겹침 시 409 TRANSACTION_RENTAL_OVERLAP. "
                    + "self 거래 차단(403). rentalStart >= rentalEnd 면 400.")
    @PostMapping("/rental-request")
    public ResponseEntity<ApiResponse<RentalRequestResponse>> rentalRequest(
            @AuthenticationPrincipal Long buyerId,
            @PathVariable("itemId") Long itemId,
            @Valid @RequestBody RentalRequestRequest request
    ) {
        Long txId = transactionService.createRentalRequest(
                buyerId, itemId, request.rentalStart(), request.rentalEnd(), request.chatRoomId()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new RentalRequestResponse(txId)));
    }

    public record RentalRequestResponse(Long transactionId) { }
}
