package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.DepositType;
import com.sseulang.domain.item.domain.TradeType;

import java.util.Set;

public record ItemForTransactionResult(
        Long itemId,
        Long sellerId,
        Set<TradeType> tradeTypes,
        Long salePrice,
        Long rentalPrice,
        DepositType depositType,
        Long deposit,
        // PERCENT 보증금일 때 원본 % 값(1~100). AMOUNT/없음은 null. Tx 생성 시 snapshot 으로 보존.
        Integer depositOriginalPercent
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
