package com.sseulang.domain.delivery.presentation.dto;

import com.sseulang.domain.delivery.application.dto.DeliveryCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Schema(description = "배달대행 요청 등록. 모집중 상태로 시작 → 라이더 수락 → 픽업 → 배송 → 정산.")
public record DeliveryCreateRequest(
        @Schema(description = "출발지 주소", example = "서울 강남구 테헤란로 123", maxLength = 255)
        @NotBlank @Size(max = 255) String pickupAddress,

        @Schema(description = "도착지 주소", example = "서울 송파구 올림픽로 456", maxLength = 255)
        @NotBlank @Size(max = 255) String dropoffAddress,

        @Schema(description = "물품 설명", example = "A4 서류 봉투 1개", maxLength = 255)
        @NotBlank @Size(max = 255) String itemDescription,

        @Schema(description = "라이더에게 지급할 수수료 (포인트)", example = "5000")
        @Positive long fee,

        @Schema(description = "희망 도착 시각 (현재 이후, 선택)", example = "2026-05-01T15:00:00", nullable = true)
        @Future LocalDateTime requestedDeadline,

        @Schema(description = "메모 (선택)", example = "1층 로비 보관함", maxLength = 500, nullable = true)
        @Size(max = 500) String memo
) {
    public DeliveryCreateCommand toCommand(Long requesterId) {
        return new DeliveryCreateCommand(
                requesterId, pickupAddress, dropoffAddress, itemDescription,
                fee, requestedDeadline, memo
        );
    }
}
