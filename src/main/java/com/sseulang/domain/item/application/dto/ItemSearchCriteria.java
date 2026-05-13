package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.TradeType;

import java.util.List;

public record ItemSearchCriteria(
        String q,
        Long categoryId,
        TradeType tradeType,
        Long minPrice,
        Long maxPrice,
        String tag,
        Long sellerId,
        // 정렬 우선순위 — 첫 키부터 적용. 마지막엔 항상 (createdAt desc, id desc) tiebreak 자동 부착.
        List<ItemSort> sorts
) {
    public ItemSearchCriteria {
        if (sorts == null || sorts.isEmpty()) {
            sorts = List.of(ItemSort.LATEST);
        } else {
            sorts = List.copyOf(sorts);
        }
    }

    // 단일 정렬 호환 생성자.
    public ItemSearchCriteria(String q, Long categoryId, TradeType tradeType, Long minPrice, Long maxPrice,
                              String tag, Long sellerId, ItemSort sort) {
        this(q, categoryId, tradeType, minPrice, maxPrice, tag, sellerId,
                sort == null ? List.of(ItemSort.LATEST) : List.of(sort));
    }

    public ItemSort sort() {
        return sorts.get(0);
    }

    public static ItemSearchCriteria empty() {
        return new ItemSearchCriteria(null, null, null, null, null, null, null, List.of(ItemSort.LATEST));
    }
}
