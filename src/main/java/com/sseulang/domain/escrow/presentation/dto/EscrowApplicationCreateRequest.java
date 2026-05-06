package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowApplicationCreateCommand;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "거래대행 폼 제출 — 수신자가 호출. linkToken 으로 link 매칭 후 atomic claim + application 생성.")
public record EscrowApplicationCreateRequest(
        @NotBlank @Size(min = 36, max = 36) String linkToken,
        @PositiveOrZero @Schema(description = "INTERNAL > 0, EXTERNAL = 0", example = "1800000")
        long itemPrice,
        @NotBlank @Size(max = 500) String itemDescription,
        @NotBlank @Size(max = 255) String pickupAddress,
        @NotNull @DecimalMin("33") @DecimalMax("39") BigDecimal pickupLat,
        @NotNull @DecimalMin("124") @DecimalMax("132") BigDecimal pickupLng,
        @NotBlank @Size(max = 255) String deliveryAddress,
        @NotNull @DecimalMin("33") @DecimalMax("39") BigDecimal deliveryLat,
        @NotNull @DecimalMin("124") @DecimalMax("132") BigDecimal deliveryLng,
        @NotNull Weight weight,
        @NotNull Volume volume,
        @NotNull Fragility fragility,
        @Size(max = 500) String deliveryNotes,
        // 프론트 calcFees 결과 — 백엔드 ±10원 검증
        @PositiveOrZero long deliveryFee,
        @PositiveOrZero long commissionFee,
        @PositiveOrZero long totalFee,
        @NotNull BigDecimal distanceKm,
        // S3 업로드된 이미지 key/url
        List<@Size(max = 500) String> imageUrls
) {
    public EscrowApplicationCreateCommand toCommand(Long receiverId) {
        return new EscrowApplicationCreateCommand(
                receiverId, linkToken,
                itemPrice, itemDescription,
                pickupAddress, pickupLat, pickupLng,
                deliveryAddress, deliveryLat, deliveryLng,
                weight, volume, fragility, deliveryNotes,
                deliveryFee, commissionFee, totalFee, distanceKm,
                imageUrls
        );
    }
}
