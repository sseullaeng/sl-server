package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;
import java.util.Set;

// 라운드 12 — admin item 목록용. 일반 ItemSummary 에 reportCount 추가 + sellerNickname 노출.
public record AdminItemSummaryResult(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        String thumbnailUrl,
        boolean rentalActive,
        Set<TradeType> tradeTypes,
        Long salePrice,
        Long rentalPrice,
        TradeType tradeType,           // legacy
        long price,                    // legacy
        Long categoryId,
        ItemStatus status,
        String region,
        int viewCount,
        int wishlistCount,
        long reportCount,
        LocalDateTime createdAt
) {
    public static AdminItemSummaryResult from(Item item, String sellerNickname, long reportCount) {
        return from(item, sellerNickname, reportCount, false);
    }

    public static AdminItemSummaryResult from(Item item, String sellerNickname, long reportCount, boolean rentalActive) {
        return new AdminItemSummaryResult(
                item.getId(),
                item.getSellerId(),
                sellerNickname,
                item.getTitle(),
                item.getThumbnailUrl(),
                rentalActive,
                item.getTradeTypes(),
                item.getSalePrice(),
                item.getRentalPrice(),
                item.getTradeType(),
                item.getPrice(),
                item.getCategoryId(),
                item.getStatus(),
                item.getRegion(),
                item.getViewCount(),
                item.getWishlistCount(),
                reportCount,
                item.getCreatedAt()
        );
    }
}
