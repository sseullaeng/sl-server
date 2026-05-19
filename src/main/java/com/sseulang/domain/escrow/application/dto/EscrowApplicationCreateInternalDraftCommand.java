package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EscrowApplicationCreateInternalDraftCommand(
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
        LocalDateTime rentalStartAt,
        LocalDateTime rentalEndAt,
        Weight weight,
        Volume volume,
        Fragility fragility,
        String deliveryNotes,
        List<String> imageUrls
) {
}
