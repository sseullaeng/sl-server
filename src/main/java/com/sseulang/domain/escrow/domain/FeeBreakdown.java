package com.sseulang.domain.escrow.domain;

import java.math.BigDecimal;

/**
 * 수수료 계산 결과 (snapshot).
 *
 * @param distanceKm        haversine 산정 거리 (소수점 2)
 * @param deliveryFee       라이더 보상 (원)
 * @param commissionFee     플랫폼 수익 (Mode B 만 > 0)
 * @param totalFee          buyer 가 부담할 총액 (Mode B = item + delivery + commission, Mode A = delivery only)
 * @param commissionRate    적용된 수수료율 (snapshot)
 */
public record FeeBreakdown(
        BigDecimal distanceKm,
        long deliveryFee,
        long commissionFee,
        long totalFee,
        BigDecimal commissionRate
) {
}
