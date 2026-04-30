package com.sseulang.domain.delivery.application.dto;

import com.sseulang.domain.delivery.domain.DeliveryRequest;
import com.sseulang.domain.delivery.domain.DeliveryStatus;

import java.time.LocalDateTime;

public record DeliveryResult(
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
    public static DeliveryResult from(DeliveryRequest d) {
        return new DeliveryResult(
                d.getId(), d.getRequesterId(), d.getRiderId(),
                d.getPickupAddress(), d.getDropoffAddress(), d.getItemDescription(),
                d.getFee(), d.getRequestedDeadline(), d.getMemo(),
                d.getStatus(), d.getRequestedAt(), d.getAcceptedAt(),
                d.getPickedUpAt(), d.getDeliveredAt(), d.getCompletedAt(),
                d.getCanceledAt(), d.getCancelReason()
        );
    }
}
