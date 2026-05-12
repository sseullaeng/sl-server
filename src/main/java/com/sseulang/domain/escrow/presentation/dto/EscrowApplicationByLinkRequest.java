package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowApplicationByLinkCommand;
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

@Schema(description = "거래대행 by-link 신청 (수신자). 발급자가 link 발급 시 본인 영역을 미리 입력했고, "
        + "수신자는 본인 영역만 채움. link.role=seller → buyer 가 delivery+receiverPhone, "
        + "link.role=buyer → seller 가 pickup+물품. 서비스 단에서 role 검증.")
public record EscrowApplicationByLinkRequest(
        @NotBlank @Size(min = 36, max = 36) String linkToken,

        // role=seller link 일 때 수신자(buyer) 입력
        @Size(max = 255) String deliveryAddress,
        @DecimalMin("33") @DecimalMax("39") BigDecimal deliveryLat,
        @DecimalMin("124") @DecimalMax("132") BigDecimal deliveryLng,
        @Size(max = 20) String receiverPhone,

        // role=buyer link 일 때 수신자(seller) 입력
        @Size(max = 255) String pickupAddress,
        @DecimalMin("33") @DecimalMax("39") BigDecimal pickupLat,
        @DecimalMin("124") @DecimalMax("132") BigDecimal pickupLng,
        @PositiveOrZero Long itemPrice,
        @Size(max = 500) String itemDescription,
        Weight weight,
        Volume volume,
        Fragility fragility,
        @Size(max = 500) String deliveryNotes,
        List<@Size(max = 500) String> imageUrls,

        // preview fee snapshot — ±10원 검증
        @PositiveOrZero long deliveryFee,
        @PositiveOrZero long commissionFee,
        @PositiveOrZero long totalFee,
        @NotNull BigDecimal distanceKm
) {
    public EscrowApplicationByLinkCommand toCommand(Long receiverId) {
        return new EscrowApplicationByLinkCommand(
                receiverId, linkToken,
                deliveryAddress, deliveryLat, deliveryLng, receiverPhone,
                pickupAddress, pickupLat, pickupLng,
                itemPrice, itemDescription,
                weight, volume, fragility, deliveryNotes, imageUrls,
                deliveryFee, commissionFee, totalFee, distanceKm
        );
    }
}
