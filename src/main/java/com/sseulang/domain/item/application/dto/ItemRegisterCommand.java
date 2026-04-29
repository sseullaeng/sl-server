package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;

import java.util.List;

public record ItemRegisterCommand(
        Long sellerId,
        Long categoryId,
        String title,
        String description,
        long price,
        Long deposit,
        RentalUnit rentalUnit,
        TradeType tradeType,
        String region,
        List<String> imageUrls,
        List<String> hashtags
) { }
