package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.TradeType;

import java.util.Set;

public record ItemForTransactionResult(
        Long itemId,
        Long sellerId,
        Set<TradeType> tradeTypes,
        Long salePrice,
        Long rentalPrice,
        Long deposit
) {
    public Long priceFor(TradeType mode) {
        if (mode == null || !tradeTypes.contains(mode)) {
            return null;
        }
        return switch (mode) {
            case 판매 -> salePrice;
            case 대여 -> rentalPrice;
            case 나눔 -> 0L;
        };
    }
}
