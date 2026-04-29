package com.sseulang.domain.payment.presentation.dto;

import com.sseulang.domain.payment.application.dto.ChargeStartCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChargeStartRequest(@NotNull @Positive Long amount) {
    public ChargeStartCommand toCommand(Long userId) {
        return new ChargeStartCommand(userId, amount);
    }
}
