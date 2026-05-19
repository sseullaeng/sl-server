package com.sseulang.domain.escrow.domain;

import java.math.BigDecimal;

public record FeeBreakdown(
        BigDecimal distanceKm,
        long deliveryFee,
        long commissionFee,
        long totalFee,
        BigDecimal commissionRate
) {
}
