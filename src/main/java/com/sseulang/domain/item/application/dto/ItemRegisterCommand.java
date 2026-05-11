package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.DepositType;
import com.sseulang.domain.item.domain.TradeType;

import java.util.List;
import java.util.Set;

public record ItemRegisterCommand(
        Long sellerId,
        Long categoryId,
        String title,
        String description,
        Set<TradeType> tradeTypes,
        Long salePrice,
        Long rentalPrice,
        Long deposit,
        DepositType depositType,
        RentalUnit rentalUnit,
        String region,
        List<String> imageUrls,
        List<String> hashtags
) {
    public static ItemRegisterCommand legacy(
            Long sellerId, Long categoryId, String title, String description,
            long price, Long deposit, RentalUnit rentalUnit, TradeType tradeType,
            String region, List<String> imageUrls, List<String> hashtags
    ) {
        Long salePrice = tradeType == TradeType.판매 ? price : null;
        Long rentalPrice = tradeType == TradeType.대여 ? price : null;
        return new ItemRegisterCommand(
                sellerId, categoryId, title, description,
                java.util.EnumSet.of(tradeType),
                salePrice, rentalPrice,
                deposit, tradeType == TradeType.대여 ? DepositType.AMOUNT : null,
                rentalUnit, region, imageUrls, hashtags
        );
    }
}
