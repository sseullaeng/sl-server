package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemHashtag;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.DepositType;
import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record ItemSummaryResult(
        Long id,
        Long sellerId,
        Long categoryId,
        String title,
        long price,
        Long salePrice,
        Long rentalPrice,
        DepositType depositType,
        TradeType tradeType,
        Set<TradeType> tradeTypes,
        ItemStatus status,
        String region,
        String thumbnailUrl,
        int wishlistCount,
        boolean isWishlisted,
        int viewCount,
        List<String> hashtags,
        LocalDateTime createdAt
) {

    public static ItemSummaryResult from(Item item) {
        return from(item, false, null);
    }

    public static ItemSummaryResult from(Item item, boolean isWishlisted) {
        return from(item, isWishlisted, null);
    }

    public static ItemSummaryResult from(Item item, boolean isWishlisted, List<String> hashtagsOverride) {
        List<String> tags = hashtagsOverride != null
                ? hashtagsOverride
                : item.getHashtags().stream().map(ItemHashtag::getTag).toList();
        return new ItemSummaryResult(
                item.getId(),
                item.getSellerId(),
                item.getCategoryId(),
                item.getTitle(),
                item.getPrice(),
                item.getSalePrice(),
                item.getRentalPrice(),
                item.getTradeTypes().contains(TradeType.대여) ? item.getDepositType() : null,
                item.getTradeType(),
                item.getTradeTypes(),
                item.getStatus(),
                item.getRegion(),
                item.getThumbnailUrl(),
                item.getWishlistCount(),
                isWishlisted,
                item.getViewCount(),
                tags,
                item.getCreatedAt()
        );
    }
}
