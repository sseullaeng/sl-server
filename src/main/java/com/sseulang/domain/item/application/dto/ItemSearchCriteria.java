package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.TradeType;

public record ItemSearchCriteria(
        String q,
        Long categoryId,
        TradeType tradeType,
        Long minPrice,
        Long maxPrice,
        String tag,
        Long sellerId,
        ItemSort sort
) {
    public ItemSearchCriteria {
        if (sort == null) sort = ItemSort.LATEST;
    }

    public static ItemSearchCriteria empty() {
        return new ItemSearchCriteria(null, null, null, null, null, null, null, ItemSort.LATEST);
    }
}
