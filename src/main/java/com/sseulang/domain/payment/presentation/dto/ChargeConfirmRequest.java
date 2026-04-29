package com.sseulang.domain.payment.presentation.dto;

import com.sseulang.domain.payment.application.dto.ChargeConfirmCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChargeConfirmRequest(
        @NotBlank String paymentKey,
        @NotBlank String orderId,
        @NotNull @Positive Long amount
) {
    public ChargeConfirmCommand toCommand(Long requesterId) {
        return new ChargeConfirmCommand(requesterId, paymentKey, orderId, amount);
    }
}
