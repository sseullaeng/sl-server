package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;

/**
 * 목록·검색·찜·내물품 공통 요약. {@code thumbnailUrl} 은 V11 denormalize 컬럼에서 직접,
 * {@code isWishlisted} 는 ApplicationService 가 viewer 기준으로 enrich 단계에서 매핑한다.
 */
public record ItemSummaryResult(
        Long id,
        Long sellerId,
        Long categoryId,
        String title,
        long price,
        TradeType tradeType,
        ItemStatus status,
        String region,
        String thumbnailUrl,
        int wishlistCount,
        boolean isWishlisted,
        int viewCount,
        LocalDateTime createdAt
) {
    /** viewer 가 없거나 비로그인 사용자 — isWishlisted = false. */
    public static ItemSummaryResult from(Item item) {
        return from(item, false);
    }

    public static ItemSummaryResult from(Item item, boolean isWishlisted) {
        return new ItemSummaryResult(
                item.getId(),
                item.getSellerId(),
                item.getCategoryId(),
                item.getTitle(),
                item.getPrice(),
                item.getTradeType(),
                item.getStatus(),
                item.getRegion(),
                item.getThumbnailUrl(),
                item.getWishlistCount(),
                isWishlisted,
                item.getViewCount(),
                item.getCreatedAt()
        );
    }
}
