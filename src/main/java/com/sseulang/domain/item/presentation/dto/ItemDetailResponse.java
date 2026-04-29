package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemDetailResult;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;
import java.util.List;

public record ItemDetailResponse(
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
        List<ItemImageResponse> images,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ItemDetailResponse from(ItemDetailResult r) {
        return new ItemDetailResponse(
                r.id(), r.sellerId(), r.categoryId(),
                r.title(), r.description(),
                r.price(), r.deposit(), r.rentalUnit(), r.tradeType(),
                r.status(), r.region(),
                r.viewCount(), r.wishlistCount(),
                r.images().stream().map(ItemImageResponse::from).toList(),
                r.createdAt(), r.updatedAt()
        );
    }
}
