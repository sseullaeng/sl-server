package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;

public record EscrowApplicationPreviewCommand(
        TradeMode tradeMode,
        long itemPrice,
        BigDecimal pickupLat,
        BigDecimal pickupLng,
        BigDecimal deliveryLat,
        BigDecimal deliveryLng,
        Weight weight,
        Volume volume,
        Fragility fragility,
        FeePayer feePayer
) {
}
