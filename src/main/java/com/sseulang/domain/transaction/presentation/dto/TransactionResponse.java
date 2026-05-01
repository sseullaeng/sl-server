package com.sseulang.domain.transaction.presentation.dto;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.transaction.application.dto.TransactionResult;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "거래 — 채팅중→예약→거래완료 상태 머신. 정산은 거래완료 시 buyer 차감/seller 적립.")
public record TransactionResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "42") Long itemId,
        @Schema(example = "100") Long sellerId,
        @Schema(example = "200") Long buyerId,
        TradeType tradeType,
        @Schema(example = "1200000") long price,
        @Schema(example = "100000", description = "대여 보증금 (판매/나눔은 null)") Long deposit,
        LocalDateTime rentalStart,
        LocalDateTime rentalEnd,
        TransactionStatus status,
        LocalDateTime reservedAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        @Schema(example = "구매자 변심") String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TransactionResponse from(TransactionResult r) {
        return new TransactionResponse(
                r.id(), r.itemId(), r.sellerId(), r.buyerId(),
                r.tradeType(), r.price(), r.deposit(),
                r.rentalStart(), r.rentalEnd(),
                r.status(), r.reservedAt(), r.completedAt(), r.canceledAt(),
                r.cancelReason(),
                r.createdAt(), r.updatedAt()
        );
    }
}
