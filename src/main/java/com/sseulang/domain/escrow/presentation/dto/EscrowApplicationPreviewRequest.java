package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowApplicationPreviewCommand;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "거래대행 수수료 미리보기 (실시간) — 좌표·물품 정보·feePayer 받아 buyer/seller 부담분 응답.")
public record EscrowApplicationPreviewRequest(
        @Schema(example = "INTERNAL", allowableValues = {"INTERNAL", "EXTERNAL"})
        @NotNull TradeMode tradeMode,

        @Schema(example = "30000", description = "Mode B(INTERNAL)는 > 0, Mode A(EXTERNAL)는 0.")
        @Min(0) long itemPrice,

        @Schema(example = "37.5665") @NotNull BigDecimal pickupLat,
        @Schema(example = "126.9780") @NotNull BigDecimal pickupLng,
        @Schema(example = "37.5172") @NotNull BigDecimal deliveryLat,
        @Schema(example = "127.0473") @NotNull BigDecimal deliveryLng,

        @NotNull Weight weight,
        @NotNull Volume volume,
        @NotNull Fragility fragility,

        @Schema(example = "both", allowableValues = {"buyer", "seller", "both"})
        @NotNull FeePayer feePayer
) {
    public EscrowApplicationPreviewCommand toCommand() {
        return new EscrowApplicationPreviewCommand(
                tradeMode, itemPrice,
                pickupLat, pickupLng, deliveryLat, deliveryLng,
                weight, volume, fragility, feePayer
        );
    }
}
