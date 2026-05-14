package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.AdminItemSummaryResult;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;
import java.util.Set;

public record AdminItemSummaryResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        String thumbnailUrl,
        boolean rentalActive,
        Set<TradeType> tradeTypes,
        Long salePrice,
        Long rentalPrice,
        TradeType tradeType,        // legacy
        long price,                 // legacy
        Long categoryId,
        ItemStatus status,
        String region,
        int viewCount,
        int wishlistCount,
        long reportCount,
        LocalDateTime createdAt
) {
    public static AdminItemSummaryResponse from(AdminItemSummaryResult r) {
        return new AdminItemSummaryResponse(
                r.id(), r.sellerId(), r.sellerNickname(),
                r.title(), r.thumbnailUrl(), r.rentalActive(),
                r.tradeTypes(), r.salePrice(), r.rentalPrice(), r.tradeType(), r.price(),
                r.categoryId(), r.status(), r.region(),
                r.viewCount(), r.wishlistCount(), r.reportCount(),
                r.createdAt()
        );
    }
}
