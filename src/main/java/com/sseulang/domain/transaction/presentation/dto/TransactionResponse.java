package com.sseulang.domain.transaction.presentation.dto;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.transaction.application.dto.TransactionResult;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "거래 — 채팅중→예약→인계완료→거래완료 (라운드 11). 정산은 인수확인 시점에 buyer hold 해제 + seller 적립.")
public record TransactionResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "42") Long itemId,
        @Schema(example = "100") Long sellerId,
        @Schema(example = "200") Long buyerId,
        @Schema(example = "45",
                description = "페어 EscrowApplication id (escrow 자동 생성 paired Tx 만 not-null). "
                        + "프론트가 일반 거래 액션 버튼 숨김 분기에 사용 — null 이면 직거래, not-null 이면 거래대행 페어.",
                nullable = true)
        Long escrowApplicationId,
        TradeType tradeType,
        @Schema(example = "1200000") long price,
        @Schema(example = "100000", description = "대여 보증금 (판매/나눔은 null)") Long deposit,
        LocalDateTime rentalStart,
        LocalDateTime rentalEnd,
        TransactionStatus status,
        LocalDateTime reservedAt,
        @Schema(description = "seller 인계확인 시각 (라운드 11)") LocalDateTime handoverConfirmedAt,
        @Schema(description = "buyer 인수확인 시각 (라운드 11)") LocalDateTime receiveConfirmedAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        @Schema(example = "구매자 변심") String cancelReason,
        @Schema(example = "1200000",
                description = "예약 시 buyer point_balance 에서 hold 한 금액 (라운드 11). 0 = 나눔 또는 옛 거래.")
        long escrowHoldAmount,
        @Schema(description = "대여 한정 — buyer 가 [반납] 누른 시각. 7일 후 자동 거래완료 카운트다운 기준.")
        LocalDateTime returnRequestedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TransactionResponse from(TransactionResult r) {
        return new TransactionResponse(
                r.id(), r.itemId(), r.sellerId(), r.buyerId(),
                r.escrowApplicationId(),
                r.tradeType(), r.price(), r.deposit(),
                r.rentalStart(), r.rentalEnd(),
                r.status(),
                r.reservedAt(),
                r.handoverConfirmedAt(), r.receiveConfirmedAt(),
                r.completedAt(), r.canceledAt(),
                r.cancelReason(),
                r.escrowHoldAmount(),
                r.returnRequestedAt(),
                r.createdAt(), r.updatedAt()
        );
    }
}
