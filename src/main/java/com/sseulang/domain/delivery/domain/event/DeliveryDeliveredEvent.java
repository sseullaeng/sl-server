package com.sseulang.domain.delivery.domain.event;

public record DeliveryDeliveredEvent(
        Long deliveryId,
        Long escrowApplicationId
) {
}
