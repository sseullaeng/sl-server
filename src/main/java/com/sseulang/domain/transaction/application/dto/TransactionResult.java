package com.sseulang.domain.transaction.application.dto;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionStatus;

import java.time.LocalDateTime;

public record TransactionResult(
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
    public static TransactionResult from(Transaction t) {
        return new TransactionResult(
                t.getId(), t.getItemId(), t.getSellerId(), t.getBuyerId(),
                t.getTradeType(), t.getPrice(), t.getDeposit(),
                t.getRentalStart(), t.getRentalEnd(),
                t.getStatus(), t.getReservedAt(), t.getCompletedAt(), t.getCanceledAt(),
                t.getCancelReason(),
                t.getCreatedAt(), t.getUpdatedAt()
        );
    }
}
