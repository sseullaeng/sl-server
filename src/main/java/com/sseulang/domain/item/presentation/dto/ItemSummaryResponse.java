package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemSummaryResult;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;

public record ItemSummaryResponse(
        Long id,
        Long sellerId,
        Long categoryId,
        String title,
        long price,
        TradeType tradeType,
        ItemStatus status,
        String region,
        LocalDateTime createdAt
) {
    public static ItemSummaryResponse from(ItemSummaryResult r) {
        return new ItemSummaryResponse(
                r.id(), r.sellerId(), r.categoryId(),
                r.title(), r.price(),
                r.tradeType(), r.status(), r.region(),
                r.createdAt()
        );
    }
}
