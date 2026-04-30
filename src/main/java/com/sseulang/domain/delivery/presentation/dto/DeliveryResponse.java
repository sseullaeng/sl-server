package com.sseulang.domain.delivery.presentation.dto;

import com.sseulang.domain.delivery.application.dto.DeliveryResult;
import com.sseulang.domain.delivery.domain.DeliveryStatus;

import java.time.LocalDateTime;

public record DeliveryResponse(
        Long id,
        Long requesterId,
        Long riderId,
        String pickupAddress,
        String dropoffAddress,
        String itemDescription,
        long fee,
        LocalDateTime requestedDeadline,
        String memo,
        DeliveryStatus status,
        LocalDateTime requestedAt,
        LocalDateTime acceptedAt,
        LocalDateTime pickedUpAt,
        LocalDateTime deliveredAt,
        LocalDateTime completedAt,
        LocalDateTime canceledAt,
        String cancelReason
) {
    public static DeliveryResponse from(DeliveryResult r) {
        return new DeliveryResponse(
                r.id(), r.requesterId(), r.riderId(),
                r.pickupAddress(), r.dropoffAddress(), r.itemDescription(),
                r.fee(), r.requestedDeadline(), r.memo(),
                r.status(), r.requestedAt(), r.acceptedAt(),
                r.pickedUpAt(), r.deliveredAt(), r.completedAt(),
                r.canceledAt(), r.cancelReason()
        );
    }
}
