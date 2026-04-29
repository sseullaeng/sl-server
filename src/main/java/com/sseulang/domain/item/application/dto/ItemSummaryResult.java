package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;

/**
 * 목록용 요약. 썸네일은 N+1 회피를 위해 본 PR 에선 미포함 — 검색·필터 PR 에서 fetch 최적화 후 추가.
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
        LocalDateTime createdAt
) {
    public static ItemSummaryResult from(Item item) {
        return new ItemSummaryResult(
                item.getId(),
                item.getSellerId(),
                item.getCategoryId(),
                item.getTitle(),
                item.getPrice(),
                item.getTradeType(),
                item.getStatus(),
                item.getRegion(),
                item.getCreatedAt()
        );
    }
}
