package com.sseulang.domain.delivery.presentation.dto;

import com.sseulang.domain.delivery.application.dto.DeliveryCreateCommand;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record DeliveryCreateRequest(
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotBlank @Size(max = 255) String dropoffAddress,
        @NotBlank @Size(max = 255) String itemDescription,
        @Positive long fee,
        @Future LocalDateTime requestedDeadline,
        @Size(max = 500) String memo
) {
    public DeliveryCreateCommand toCommand(Long requesterId) {
        return new DeliveryCreateCommand(
                requesterId, pickupAddress, dropoffAddress, itemDescription,
                fee, requestedDeadline, memo
        );
    }
}
