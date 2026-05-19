package com.sseulang.domain.transaction.application.dto;

import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;

public record PendingReviewableResult(
        Long transactionId,
        Long itemId,
        Long revieweeId,
        TradeType tradeType,
        long price,
        LocalDateTime completedAt,
        LocalDateTime deadline,
        String itemThumbnailUrl
) {}
