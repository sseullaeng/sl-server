package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;
import java.util.List;

public record EscrowLinkCreateCommand(
        Long initiatorId,
        InitiatorRole initiatorRole,
        FeePayer feePayer,
        TradeMode tradeMode,
        // seller 발급 시
        String initiatorPickupAddress,
        BigDecimal initiatorPickupLat,
        BigDecimal initiatorPickupLng,
        Long initiatorItemPrice,
        String initiatorItemDescription,
        Weight initiatorWeight,
        Volume initiatorVolume,
        Fragility initiatorFragility,
        String initiatorDeliveryNotes,
        List<String> initiatorImageUrls,
        // buyer 발급 시
        String initiatorDeliveryAddress,
        BigDecimal initiatorDeliveryLat,
        BigDecimal initiatorDeliveryLng,
        String initiatorReceiverPhone
) {
    public static EscrowLinkCreateCommand legacy(
            Long initiatorId, InitiatorRole role, FeePayer feePayer, TradeMode tradeMode
    ) {
        return new EscrowLinkCreateCommand(
                initiatorId, role, feePayer, tradeMode,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null
        );
    }
}
