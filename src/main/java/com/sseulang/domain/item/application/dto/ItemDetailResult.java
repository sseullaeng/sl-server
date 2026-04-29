package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemHashtag;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;
import java.util.List;

public record ItemDetailResult(
        Long id,
        Long sellerId,
        Long categoryId,
        String title,
        String description,
        long price,
        Long deposit,
        RentalUnit rentalUnit,
        TradeType tradeType,
        ItemStatus status,
        String region,
        int viewCount,
        int wishlistCount,
        List<ItemImageResult> images,
        List<String> hashtags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ItemDetailResult from(Item item) {
        return new ItemDetailResult(
                item.getId(),
                item.getSellerId(),
                item.getCategoryId(),
                item.getTitle(),
                item.getDescription(),
                item.getPrice(),
                item.getDeposit(),
                item.getRentalUnit(),
                item.getTradeType(),
                item.getStatus(),
                item.getRegion(),
                item.getViewCount(),
                item.getWishlistCount(),
                item.getImages().stream().map(ItemImageResult::from).toList(),
                item.getHashtags().stream().map(ItemHashtag::getTag).toList(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
