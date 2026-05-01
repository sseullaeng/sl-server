package com.sseulang.domain.delivery.presentation.dto;

import com.sseulang.domain.delivery.application.dto.DeliveryResult;
import com.sseulang.domain.delivery.domain.DeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "배달 요청 — 모집중→수락→배송중→배송완료→정산완료. 정산 시 요청자 차감/라이더 적립.")
public record DeliveryResponse(
        @Schema(example = "55") Long id,
        @Schema(example = "100") Long requesterId,
        @Schema(description = "수락 후 채워짐", example = "200") Long riderId,
        @Schema(example = "서울 강남구 테헤란로 123") String pickupAddress,
        @Schema(example = "서울 송파구 올림픽로 456") String dropoffAddress,
        @Schema(example = "A4 서류 봉투 1개") String itemDescription,
        @Schema(example = "5000", description = "라이더 수수료 — 정산 시 요청자 잔액에서 차감") long fee,
        LocalDateTime requestedDeadline,
        @Schema(example = "1층 로비 보관함") String memo,
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
