package com.sseulang.domain.delivery.presentation;

import com.sseulang.domain.delivery.application.DeliveryApplicationService;
import com.sseulang.domain.delivery.presentation.dto.DeliveryCancelRequest;
import com.sseulang.domain.delivery.presentation.dto.DeliveryCreateRequest;
import com.sseulang.domain.delivery.presentation.dto.DeliveryResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "배달 요청 등록 (요청자)",
            description = "이메일 인증 필수. 등록 시점에 fee escrow 안 함 — 정산(complete)에서 차감.")
    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryResponse>> create(
            @AuthenticationPrincipal Long requesterId,
            @Valid @RequestBody DeliveryCreateRequest request
    ) {
        DeliveryResponse body = DeliveryResponse.from(deliveryService.create(request.toCommand(requesterId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(body));
    }

    @Operation(summary = "모집중 배달 목록",
            description = "라이더가 수락 가능한 후보. 본인 등록 여부 필터는 클라이언트 책임.")
    @GetMapping
    public ApiResponse<PageResponse<DeliveryResponse>> listOpen(Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(
                deliveryService.listOpen(pageable).map(DeliveryResponse::from)
        ));
    }

    @Operation(summary = "내 배달 목록 (요청+수락 포함)",
            description = "본인이 requester 또는 rider 로 참여한 배달 페이징.")
    @GetMapping("/me")
    public ApiResponse<PageResponse<DeliveryResponse>> listMine(
            @AuthenticationPrincipal Long userId,
            Pageable pageable
    ) {
        return ApiResponse.ok(PageResponse.from(
                deliveryService.listMine(userId, pageable).map(DeliveryResponse::from)
        ));
    }

    @Operation(summary = "배달 단건 조회",
            description = "모집중은 누구나 조회 가능. 그 외 상태는 참여자(requester/rider)만.")
    @GetMapping("/{id}")
    public ApiResponse<DeliveryResponse> getOne(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.getById(id, userId)));
    }

    @Operation(summary = "라이더 수락",
            description = "이메일 인증 필수. 본인 거래 차단(400 DELIVERY_SELF_NOT_ALLOWED). "
                    + "동시 수락 race 시 한 명만 성공(409 DELIVERY_ALREADY_ACCEPTED).")
    @PatchMapping("/{id}/accept")
    public ApiResponse<DeliveryResponse> accept(
            @AuthenticationPrincipal Long riderId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.accept(id, riderId)));
    }

    @Operation(summary = "라이더 픽업 완료",
            description = "수락한 라이더만. 수락 → 배송중 전이.")
    @PatchMapping("/{id}/pickup")
    public ApiResponse<DeliveryResponse> pickup(
            @AuthenticationPrincipal Long riderId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.markPickedUp(id, riderId)));
    }

    @Operation(summary = "라이더 배송 완료",
            description = "수락한 라이더만. 배송중 → 배송완료 전이.")
    @PatchMapping("/{id}/deliver")
    public ApiResponse<DeliveryResponse> deliver(
            @AuthenticationPrincipal Long riderId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.markDelivered(id, riderId)));
    }

    @Operation(summary = "요청자 정산 확인 — 포인트 이동",
            description = "요청자만. 잔액에서 fee 차감 → 라이더 적립. 잔액 부족 400 INSUFFICIENT_POINT (전체 트랜잭션 롤백).")
    @PatchMapping("/{id}/complete")
    public ApiResponse<DeliveryResponse> complete(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long id
    ) {
        return ApiResponse.ok(DeliveryResponse.from(deliveryService.complete(id, requesterId)));
    }

    @Operation(summary = "요청자 취소",
            description = "모집중 한정. 수락 이후 취소는 400 DELIVERY_INVALID_STATE — 분쟁 흐름 별도 (follow-up).")
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
