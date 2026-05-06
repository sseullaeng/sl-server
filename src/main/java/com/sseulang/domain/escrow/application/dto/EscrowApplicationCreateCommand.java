package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;
import java.util.List;

public record EscrowApplicationCreateCommand(
        Long receiverId,                // 폼 제출자 = 수신자 후보
        String linkToken,
        long itemPrice,                 // INTERNAL > 0, EXTERNAL = 0
        String itemDescription,
        String pickupAddress,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        String deliveryAddress,
        BigDecimal deliveryLat,
        BigDecimal deliveryLng,
        Weight weight,
        Volume volume,
        Fragility fragility,
        String deliveryNotes,
        // 프론트가 보낸 fee 값 (백엔드 ±10원 검증)
        long submittedDeliveryFee,
        long submittedCommissionFee,
        long submittedTotalFee,
        BigDecimal submittedDistanceKm,
        List<String> imageUrls
) {
}
