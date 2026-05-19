package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;
import java.util.List;

public record EscrowApplicationCreateCommand(
        Long receiverId,                
        String linkToken,
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
