package com.sseulang.domain.escrow.application.dto;

import java.math.BigDecimal;

public record EscrowApplicationPreviewResult(
        BigDecimal distanceKm,
        long deliveryFee,
        long commissionFee,
        long totalFee,
        long buyerPayable,
        long sellerPayable,
        BigDecimal commissionRate
) {
}
