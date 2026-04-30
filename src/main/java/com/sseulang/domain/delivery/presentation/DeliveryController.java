package com.sseulang.domain.delivery.presentation;

import com.sseulang.domain.delivery.application.DeliveryApplicationService;
import com.sseulang.domain.delivery.presentation.dto.DeliveryCancelRequest;
import com.sseulang.domain.delivery.presentation.dto.DeliveryCreateRequest;
import com.sseulang.domain.delivery.presentation.dto.DeliveryResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Delivery", description = "배달대행 — 모집중→수락→배송중→배송완료→정산완료 (또는 취소).")
@RestController
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {

    private final DeliveryApplicationService deliveryService;

    public DeliveryController(DeliveryApplicationService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /** 요청 등록 — 요청자, requireVerified. */
    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryResponse>> create(
            @AuthenticationPrincipal Long requesterId,
            @Valid @RequestBody DeliveryCreateRequest request
    ) {
        DeliveryResponse body = DeliveryResponse.from(deliveryService.create(request.toCommand(requesterId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(body));
    }

    /** 모집중 목록 — 라이더가 수락 가능한 후보. 인증 필수, 본인 등록 여부는 클라이언트에서 필터. */
    @GetMapping
    public ApiResponse<PageResponse<DeliveryResponse>> listOpen(Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                deliveryService.listOpen(pageable).map(DeliveryResponse::from)
        ));
    }

    /** 내 요청+수락 목록. */
    @GetMapping("/me")
    public ApiResponse<PageResponse<DeliveryResponse>> listMine(
            @AuthenticationPrincipal Long userId,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                deliveryService.listMine(userId, pageable).map(DeliveryResponse::from)
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<DeliveryResponse> getOne(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.getById(id, userId)));
    }

    /** 라이더 수락. */
    @PatchMapping("/{id}/accept")
    public ApiResponse<DeliveryResponse> accept(
            @AuthenticationPrincipal Long riderId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.accept(id, riderId)));
    }

    /** 라이더 픽업 완료. */
    @PatchMapping("/{id}/pickup")
    public ApiResponse<DeliveryResponse> pickup(
            @AuthenticationPrincipal Long riderId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.markPickedUp(id, riderId)));
    }

    /** 라이더 배송 완료. */
    @PatchMapping("/{id}/deliver")
    public ApiResponse<DeliveryResponse> deliver(
            @AuthenticationPrincipal Long riderId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.markDelivered(id, riderId)));
    }

    /** 요청자 정산 확인 — 포인트 이동 + 정산완료. */
    @PatchMapping("/{id}/complete")
    public ApiResponse<DeliveryResponse> complete(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.complete(id, requesterId)));
    }

    /** 요청자 취소 — 모집중 한정. */
    @PatchMapping("/{id}/cancel")
    public ApiResponse<DeliveryResponse> cancel(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) DeliveryCancelRequest request
    ) {
        String reason = request == null ? null : request.reason();
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.cancel(id, requesterId, reason)));
    }
}
