package com.sseulang.domain.transaction.presentation.dto;

import com.sseulang.domain.transaction.application.dto.TransactionCreateCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record TransactionCreateRequest(
        @NotNull @Positive Long itemId,
        LocalDateTime rentalStart,
        LocalDateTime rentalEnd
) {
    public TransactionCreateCommand toCommand(Long buyerId) {
        return new TransactionCreateCommand(itemId, buyerId, rentalStart, rentalEnd);
    }
}
