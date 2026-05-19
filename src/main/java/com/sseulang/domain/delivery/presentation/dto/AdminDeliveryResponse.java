package com.sseulang.domain.delivery.presentation.dto;

import com.sseulang.domain.delivery.application.dto.AdminDeliveryResult;
import com.sseulang.domain.delivery.domain.DeliveryStatus;

import java.time.LocalDateTime;

public record AdminDeliveryResponse(
        Long id,
        Long requesterId, String requesterNickname,
        Long riderId, String riderNickname,
        String pickupAddress, String dropoffAddress,
        String itemDescription,
        long fee, DeliveryStatus status,
        LocalDateTime requestedAt, LocalDateTime acceptedAt,
        LocalDateTime pickedUpAt, LocalDateTime deliveredAt,
        LocalDateTime completedAt, LocalDateTime canceledAt,
        String cancelReason,
        Long escrowApplicationId
) {
    public static AdminDeliveryResponse from(AdminDeliveryResult r) {
        return new AdminDeliveryResponse(
                r.id(), r.requesterId(), r.requesterNickname(),
                r.riderId(), r.riderNickname(),
                r.pickupAddress(), r.dropoffAddress(), r.itemDescription(),
                r.fee(), r.status(),
                r.requestedAt(), r.acceptedAt(), r.pickedUpAt(),
                r.deliveredAt(), r.completedAt(),
                r.canceledAt(), r.cancelReason(),
                r.escrowApplicationId()
        );
    }
}
