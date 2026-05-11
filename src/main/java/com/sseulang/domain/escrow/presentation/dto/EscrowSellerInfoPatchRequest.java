package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowSellerInfoPatchCommand;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "판매자 영역 수정 — 정보입력대기 상태에서만 호출. 출발지/물품/가격.")
public record EscrowSellerInfoPatchRequest(
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotNull BigDecimal pickupLat,
        @NotNull BigDecimal pickupLng,
        @NotNull Weight weight,
        @NotNull Volume volume,
        @NotNull Fragility fragility,
        @Min(0) long itemPrice,
        @NotBlank @Size(max = 500) String itemDescription,
        @Size(max = 500) String deliveryNotes
) {
    public EscrowSellerInfoPatchCommand toCommand() {
        return new EscrowSellerInfoPatchCommand(
                pickupAddress, pickupLat, pickupLng,
                weight, volume, fragility,
                itemPrice, itemDescription, deliveryNotes
        );
    }
}
