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
        Long escrowApplicationId,
        TradeType tradeType,
        long price,
        Long deposit,
        Integer depositOriginalPercent,
        LocalDateTime rentalStart,
        LocalDateTime rentalEnd,
        TransactionStatus status,
        LocalDateTime reservedAt,
        LocalDateTime handoverConfirmedAt,
        LocalDateTime receiveConfirmedAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        String cancelReason,
        long escrowHoldAmount,
        LocalDateTime returnRequestedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TransactionResult from(Transaction t) {
        return new TransactionResult(
                t.getId(), t.getItemId(), t.getSellerId(), t.getBuyerId(),
                t.getEscrowApplicationId(),
                t.getTradeType(), t.getPrice(), t.getDeposit(),
                t.getDepositOriginalPercent(),
                t.getRentalStart(), t.getRentalEnd(),
                t.getStatus(),
                t.getReservedAt(),
                t.getHandoverConfirmedAt(), t.getReceiveConfirmedAt(),
                t.getCompletedAt(), t.getCanceledAt(),
                t.getCancelReason(),
                t.getEscrowHoldAmount(),
                t.getReturnRequestedAt(),
                t.getCreatedAt(), t.getUpdatedAt()
        );
    }
}
