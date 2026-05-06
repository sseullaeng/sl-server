package com.sseulang.domain.escrow.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 거래대행 수수료 계산 DomainService. 프론트 {@code calcFees} 와 동일 로직 (결정 #9, CC3).
 *
 * <pre>
 * adjustedKmRate = (truck ? truckBaseKmRate : baseKmRate)
 *                  + (fuelPricePerL - baseFuelPrice) * distance / fuelEfficiency
 * baseFee        = (truck ? truckBaseDeliveryFee : baseDeliveryFee)
 * deliveryFee    = baseFee + adjustedKmRate * distance
 * deliveryFee    = max(deliveryFee, truck ? truckMinDeliveryFee : minDeliveryFee)
 * deliveryFee    = round(deliveryFee * weightMul * volumeMul * fragMul)
 * </pre>
 *
 * <p>spring 컴포넌트 X — 순수 도메인 (의존성 없음). 호출자가 settings 주입.</p>
 */
public final class EscrowFeeCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private EscrowFeeCalculator() {}

    /**
     * 좌표 → distance (haversine). 프론트와 동일 공식.
     */
    public static BigDecimal distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double km = EARTH_RADIUS_KM * c;
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 전체 수수료 산정 (snapshot 용).
     *
     * @param settings   현재 운영 정책 (singleton row)
     * @param tradeMode  INTERNAL/EXTERNAL
     * @param itemPrice  Mode B 만 > 0
     * @param distanceKm 좌표로 사전 계산된 거리 (소수점 2)
     */
    public static FeeBreakdown calculate(
            EscrowFeeSettings settings,
            TradeMode tradeMode,
            long itemPrice,
            BigDecimal distanceKm,
            Weight weight,
            Volume volume,
            Fragility fragility
    ) {
        if (settings == null || tradeMode == null || weight == null || volume == null || fragility == null) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }
        if (tradeMode == TradeMode.INTERNAL && itemPrice <= 0) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }
        if (tradeMode == TradeMode.EXTERNAL && itemPrice != 0) {
            throw new BusinessException(ErrorCode.ESCROW_FORM_INVALID);
        }

        boolean truck = weight.isTruck();
        double dist = distanceKm.doubleValue();

        // 유류비 보정: (현재유가 - 기준유가) * 거리 / 연비 = 거리당 추가비용
        long baseKmRate = truck ? settings.getTruckBaseKmRate() : settings.getBaseKmRate();
        double fuelEff = (truck ? settings.getTruckFuelEfficiency() : settings.getFuelEfficiency()).doubleValue();
        double fuelDiff = settings.getFuelPricePerL() - settings.getBaseFuelPrice();
        double adjustedKmRate = baseKmRate + (fuelDiff * dist / Math.max(fuelEff, 0.0001));

        long baseFee = truck ? settings.getTruckBaseDeliveryFee() : settings.getBaseDeliveryFee();
        long minFee = truck ? settings.getTruckMinDeliveryFee() : settings.getMinDeliveryFee();

        double rawDelivery = baseFee + adjustedKmRate * dist;
        rawDelivery = Math.max(rawDelivery, minFee);
        rawDelivery = rawDelivery * weight.multiplier() * volume.multiplier() * fragility.multiplier();
        long deliveryFee = Math.round(rawDelivery);

        long commissionFee = 0L;
        if (tradeMode == TradeMode.INTERNAL) {
            commissionFee = Math.round(itemPrice * settings.getCommissionRate().doubleValue());
        }

        long totalFee = deliveryFee + commissionFee + (tradeMode == TradeMode.INTERNAL ? itemPrice : 0L);

        return new FeeBreakdown(
                distanceKm,
                deliveryFee,
                commissionFee,
                totalFee,
                settings.getCommissionRate()
        );
    }

    /**
     * 프론트가 보낸 fee 값을 ±10원 tolerance 로 검증 (결정 #10, EE2).
     * mismatch 시 ESCROW_FEE_MISMATCH (race 또는 위변조 의심).
     */
    public static void verifyTolerance(FeeBreakdown calculated, long submittedDeliveryFee, long submittedCommissionFee, long submittedTotalFee) {
        long tolerance = 10L;
        if (Math.abs(calculated.deliveryFee() - submittedDeliveryFee) > tolerance ||
                Math.abs(calculated.commissionFee() - submittedCommissionFee) > tolerance ||
                Math.abs(calculated.totalFee() - submittedTotalFee) > tolerance) {
            throw new BusinessException(ErrorCode.ESCROW_FEE_MISMATCH);
        }
    }
}
