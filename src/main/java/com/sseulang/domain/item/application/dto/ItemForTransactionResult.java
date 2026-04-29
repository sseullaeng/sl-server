package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.TradeType;

/**
 * 거래 도메인이 거래 생성 시 필요한 Item 정보. ItemApplicationService 가 활성 Item 만 반환.
 * 다른 도메인이 ItemRepository 를 직접 참조하지 않도록 (CLAUDE.md §3.3).
 */
public record ItemForTransactionResult(
        Long itemId,
        Long sellerId,
        TradeType tradeType,
        long price,
        Long deposit
) { }
