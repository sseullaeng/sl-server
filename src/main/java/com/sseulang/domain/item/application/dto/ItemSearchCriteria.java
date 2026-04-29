package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.TradeType;

/**
 * Item 검색 조건. null/blank 필드는 무시.
 *
 * <p>키워드 q 는 LIKE 검색 (FULLTEXT 도입은 5/6 이후). 태그는 단건 매칭 — 다중 AND 는 후속 작업.</p>
 */
public record ItemSearchCriteria(
        String q,
        Long categoryId,
        TradeType tradeType,
        Long minPrice,
        Long maxPrice,
        String tag
) {
    public static ItemSearchCriteria empty() {
        return new ItemSearchCriteria(null, null, null, null, null, null);
    }
}
