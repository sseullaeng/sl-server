package com.sseulang.domain.escrow.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "거래대행 수수료 정책 변경 (admin). 결정 #9 — DB 단일 row 갱신. 진행 중 신청 영향 X (snapshot).")
public record EscrowFeeSettingsPatchRequest(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal commissionRate,
        @NotNull @Positive Long fuelPricePerL,
        @NotNull @Positive Long baseFuelPrice,
        @NotNull @Positive Long baseDeliveryFee,
        @NotNull @Positive Long baseKmRate,
        @NotNull @DecimalMin("0.01") BigDecimal fuelEfficiency,
        @NotNull @Positive Long minDeliveryFee,
        @NotNull @Positive Long truckBaseDeliveryFee,
        @NotNull @Positive Long truckBaseKmRate,
        @NotNull @DecimalMin("0.01") BigDecimal truckFuelEfficiency,
        @NotNull @Positive Long truckMinDeliveryFee
) {
}
