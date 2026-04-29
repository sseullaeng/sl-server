package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemUpdateCommand;
import com.sseulang.domain.item.domain.RentalUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ItemUpdateRequest(
        Long categoryId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        @NotNull @PositiveOrZero Long price,
        @PositiveOrZero Long deposit,
        RentalUnit rentalUnit,
        @Size(max = 100) String region,
        List<String> imageUrls,
        List<String> hashtags
) {
    public ItemUpdateCommand toCommand() {
        return new ItemUpdateCommand(
                categoryId, title, description, price, deposit, rentalUnit, region, imageUrls, hashtags
        );
    }
}
