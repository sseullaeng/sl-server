package com.sseulang.domain.delivery.presentation;

import com.sseulang.domain.delivery.application.DeliveryLocationApplicationService;
import com.sseulang.domain.delivery.presentation.dto.DeliveryLocationMessage;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class DeliveryLocationStompController {

    private final DeliveryLocationApplicationService locationService;

    public DeliveryLocationStompController(DeliveryLocationApplicationService locationService) {
        this.locationService = locationService;
    }

    @MessageMapping("/delivery/{deliveryId}/location")
    public void onLocation(
            @DestinationVariable Long deliveryId,
            @Valid @Payload DeliveryLocationMessage msg,
            Authentication auth
    ) {
        Long riderId = (Long) auth.getPrincipal();
        locationService.publishLocation(
                deliveryId, riderId,
                msg.latitude(), msg.longitude(),
                msg.accuracyM(), msg.recordedAt()
        );
    }
}
