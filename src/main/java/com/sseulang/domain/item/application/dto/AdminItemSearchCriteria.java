package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;

import java.time.LocalDateTime;

public record AdminItemSearchCriteria(
        String q,                       // title 또는 seller nickname 키워드
        ItemStatus status,
        TradeType tradeType,
        Long categoryId,
        LocalDateTime createdAfter,
        LocalDateTime createdBefore,
        AdminItemSort sort              // LATEST / VIEW_DESC / REPORT_DESC
) {
    public AdminItemSearchCriteria {
        if (sort == null) sort = AdminItemSort.LATEST;
    }
}
