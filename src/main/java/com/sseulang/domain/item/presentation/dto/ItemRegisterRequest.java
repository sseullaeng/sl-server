package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ItemRegisterRequest(
        Long categoryId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotNull @PositiveOrZero Long price,
        @PositiveOrZero Long deposit,
        RentalUnit rentalUnit,
        @NotNull TradeType tradeType,
        @Size(max = 100) String region,
        List<String> imageUrls
) {
    public ItemRegisterCommand toCommand(Long sellerId) {
        return new ItemRegisterCommand(
                sellerId, categoryId, title, description,
                price, deposit, rentalUnit, tradeType, region, imageUrls
        );
    }
}
