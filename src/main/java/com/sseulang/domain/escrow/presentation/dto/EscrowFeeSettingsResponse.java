package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.domain.EscrowFeeSettings;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "거래대행 수수료 정책 — 프론트 calcFees 와 동일 11 fields (CC3).")
public record EscrowFeeSettingsResponse(
        BigDecimal commissionRate,
        long fuelPricePerL,
        long baseFuelPrice,
        long baseDeliveryFee,
        long baseKmRate,
        BigDecimal fuelEfficiency,
        long minDeliveryFee,
        long truckBaseDeliveryFee,
        long truckBaseKmRate,
        BigDecimal truckFuelEfficiency,
        long truckMinDeliveryFee,
        LocalDateTime updatedAt,
        Long updatedBy
) {
    public static EscrowFeeSettingsResponse from(EscrowFeeSettings s) {
        return new EscrowFeeSettingsResponse(
                s.getCommissionRate(),
                s.getFuelPricePerL(), s.getBaseFuelPrice(),
                s.getBaseDeliveryFee(), s.getBaseKmRate(), s.getFuelEfficiency(), s.getMinDeliveryFee(),
                s.getTruckBaseDeliveryFee(), s.getTruckBaseKmRate(), s.getTruckFuelEfficiency(), s.getTruckMinDeliveryFee(),
                s.getUpdatedAt(), s.getUpdatedBy()
        );
    }
}
