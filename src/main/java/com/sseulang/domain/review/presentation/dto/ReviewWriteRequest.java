package com.sseulang.domain.review.presentation.dto;

import com.sseulang.domain.review.application.dto.ReviewWriteCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReviewWriteRequest(
        @NotNull @Positive Long transactionId,
        @Min(1) @Max(5) int rating,
        @Size(max = 500) String comment
) {
    public ReviewWriteCommand toCommand(Long reviewerId) {
        return new ReviewWriteCommand(transactionId, reviewerId, rating, comment);
    }
}
