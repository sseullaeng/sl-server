package com.sseulang.domain.escrow.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class EscrowFeeCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private EscrowFeeCalculator() {}

    

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

    

    public static void verifyTolerance(FeeBreakdown calculated, long submittedDeliveryFee, long submittedCommissionFee, long submittedTotalFee) {
        long tolerance = 10L;
        if (Math.abs(calculated.deliveryFee() - submittedDeliveryFee) > tolerance ||
                Math.abs(calculated.commissionFee() - submittedCommissionFee) > tolerance ||
                Math.abs(calculated.totalFee() - submittedTotalFee) > tolerance) {
            throw new BusinessException(ErrorCode.ESCROW_FEE_MISMATCH);
        }
    }
}
