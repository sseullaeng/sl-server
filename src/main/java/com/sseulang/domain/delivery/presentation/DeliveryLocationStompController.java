package com.sseulang.domain.delivery.presentation;

import com.sseulang.domain.delivery.application.DeliveryLocationApplicationService;
import com.sseulang.domain.delivery.presentation.dto.DeliveryLocationMessage;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * 라이더 좌표 STOMP SEND 핸들러 (follow-up #51).
 *
 * <pre>
 *   destination: /app/delivery/{deliveryId}/location
 *   payload    : DeliveryLocationMessage (lat, lng, accuracyM?, recordedAt?)
 *   broadcast  : /topic/delivery/{deliveryId}/location  (참여자만 SUBSCRIBE 가능)
 * </pre>
 *
 * <p>인증은 {@link com.sseulang.global.websocket.StompAuthChannelInterceptor} 의 CONNECT
 * 단계에서 이미 처리. 본 핸들러는 Authentication 의 principal(userId) 만 추출 후 라이더 검증을
 * ApplicationService 에 위임.</p>
 *
 * <p>SEND 자체는 화이트리스트 X — ApplicationService 가 라이더 본인 검증으로 거부.
 * 다른 사용자가 임의의 destination 으로 SEND 보내도 publishLocation 의 requireRider 가 차단.</p>
 */
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
