package com.sseulang.domain.delivery.presentation;

import com.sseulang.domain.delivery.application.DeliveryLocationApplicationService;
import com.sseulang.domain.delivery.domain.DeliveryLocation;
import com.sseulang.domain.delivery.presentation.dto.DeliveryLocationMessage;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Delivery", description = "배달 실시간 위치 (REST fallback)")
@RestController
@RequestMapping("/api/v1/deliveries/{id}/location")
public class DeliveryLocationController {

    private final DeliveryLocationApplicationService locationService;

    public DeliveryLocationController(DeliveryLocationApplicationService locationService) {
        this.locationService = locationService;
    }

    @Operation(summary = "마지막 위치 조회 (REST fallback)",
            description = "참여자만. STOMP 구독 시작 직후 마지막 좌표 즉시 표시용. "
                    + "캐시 미존재 / TTL 만료 시 204 No Content.")
    @GetMapping("/last")
    public ResponseEntity<ApiResponse<DeliveryLocationMessage>> getLast(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable("id") Long deliveryId
    ) {
        return locationService.findLast(deliveryId, viewerId)
                .map(loc -> ResponseEntity.ok(ApiResponse.ok(toMessage(loc))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NO_CONTENT).build());
    }

    private static DeliveryLocationMessage toMessage(DeliveryLocation loc) {
        return new DeliveryLocationMessage(
                loc.latitude(), loc.longitude(), loc.accuracyM(), loc.recordedAt()
        );
    }
}
