package com.sseulang.domain.escrow.application.dto;

import java.math.BigDecimal;

public record EscrowBuyerInfoPatchCommand(
        String deliveryAddress,
        BigDecimal deliveryLat,
        BigDecimal deliveryLng,
        String receiverPhone
) {
}
