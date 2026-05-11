package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemHashtag;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
public record ItemDetailResult(
        Long id,
        Long sellerId,
        Long categoryId,
        String title,
        String description,
        long price,
        Long salePrice,
        Long rentalPrice,
        Long deposit,
        RentalUnit rentalUnit,
        TradeType tradeType,
        Set<TradeType> tradeTypes,
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
                item.getSalePrice(),
                item.getRentalPrice(),
                item.getDeposit(),
                item.getRentalUnit(),
                item.getTradeType(),
                item.getTradeTypes(),
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
