package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowApplicationCreateInternalCommand;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "내부 거래대행 신청 (채팅방 + 판매자만).")
public record EscrowApplicationCreateInternalRequest(
        @Schema(example = "7") @NotNull Long chatRoomId,
        @Schema(example = "123") @NotNull Long itemId,

        @Schema(example = "INTERNAL") @NotNull TradeMode tradeMode,
        @Schema(example = "both") @NotNull FeePayer feePayer,

        @Schema(example = "30000") @Min(0) long itemPrice,
        @Schema(example = "맥북 프로") @NotBlank @Size(max = 500) String itemDescription,

        @NotBlank @Size(max = 255) String pickupAddress,
        @NotNull BigDecimal pickupLat,
        @NotNull BigDecimal pickupLng,
        @NotBlank @Size(max = 255) String deliveryAddress,
        @NotNull BigDecimal deliveryLat,
        @NotNull BigDecimal deliveryLng,
        @Schema(description = "대여 종료 예정 시각, 대여 거래대행만 필수")
        LocalDateTime rentalEndAt,

        @NotNull Weight weight,
        @NotNull Volume volume,
        @NotNull Fragility fragility,
        @Size(max = 500) String deliveryNotes,

        @Min(0) long submittedDeliveryFee,
        @Min(0) long submittedCommissionFee,
        @Min(0) long submittedTotalFee,
        @NotNull BigDecimal submittedDistanceKm,

        @Size(max = 5) List<@Size(max = 500) String> imageUrls
) {
    public EscrowApplicationCreateInternalCommand toCommand(Long requesterId) {
        return new EscrowApplicationCreateInternalCommand(
                requesterId, chatRoomId, itemId,
                tradeMode, feePayer,
                itemPrice, itemDescription,
                pickupAddress, pickupLat, pickupLng,
                deliveryAddress, deliveryLat, deliveryLng, rentalEndAt,
                weight, volume, fragility, deliveryNotes,
                submittedDeliveryFee, submittedCommissionFee, submittedTotalFee, submittedDistanceKm,
                imageUrls
        );
    }
}
