package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;

import java.util.List;
import java.util.Set;

public record ItemUpdateCommand(
        Long categoryId,
        String title,
        String description,
        Set<TradeType> tradeTypes,
        Long salePrice,
        Long rentalPrice,
        Long deposit,
        RentalUnit rentalUnit,
        String region,
        List<String> imageUrls,
        List<String> hashtags
) {
    public static ItemUpdateCommand legacy(
            Long categoryId, String title, String description,
            long price, Long deposit, RentalUnit rentalUnit,
            String region, List<String> imageUrls, List<String> hashtags
    ) {
        TradeType inferred = (deposit != null || rentalUnit != null) ? TradeType.대여 : TradeType.판매;
        Long salePrice = inferred == TradeType.판매 ? price : null;
        Long rentalPrice = inferred == TradeType.대여 ? price : null;
        return new ItemUpdateCommand(
                categoryId, title, description,
                java.util.EnumSet.of(inferred),
                salePrice, rentalPrice,
                deposit, rentalUnit, region, imageUrls, hashtags
        );
    }
}
