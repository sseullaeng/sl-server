package com.sseulang.domain.transaction.presentation.dto;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.transaction.application.dto.TransactionResult;
import com.sseulang.domain.transaction.domain.TransactionStatus;

import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        Long itemId,
        Long sellerId,
        Long buyerId,
        TradeType tradeType,
        long price,
        Long deposit,
        LocalDateTime rentalStart,
        LocalDateTime rentalEnd,
        TransactionStatus status,
        LocalDateTime reservedAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        String cancelReason,
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
