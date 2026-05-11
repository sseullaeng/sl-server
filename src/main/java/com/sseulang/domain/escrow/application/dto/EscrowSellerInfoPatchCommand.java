package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;

public record EscrowSellerInfoPatchCommand(
        String pickupAddress,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        Weight weight,
        Volume volume,
        Fragility fragility,
        long itemPrice,
        String itemDescription,
        String deliveryNotes
) {
}
