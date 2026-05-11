package com.sseulang.domain.escrow.domain.event;

public record EscrowConfirmedEvent(
        Long escrowApplicationId,
        String pickupAddress,
        double pickupLat,
        double pickupLng,
        String deliveryAddress,
        double deliveryLat,
        double deliveryLng,
        String itemDescription,
        long deliveryFee,
        Long requesterId
) {
}
