package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;
import java.util.List;

public record EscrowApplicationCreateInternalCommand(
        Long requesterId,            
        Long chatRoomId,
        Long itemId,                 
        TradeMode tradeMode,
        FeePayer feePayer,
        long itemPrice,
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
        long submittedDeliveryFee,
        long submittedCommissionFee,
        long submittedTotalFee,
        BigDecimal submittedDistanceKm,
        List<String> imageUrls
) {
}
