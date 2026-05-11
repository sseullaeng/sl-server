package com.sseulang.domain.delivery.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;

import java.time.Instant;

public record DeliveryLocation(
        double latitude,
        double longitude,
        Double accuracyM,
        Instant recordedAt
) {

    
    private static final double LAT_MIN = 33.0;
    private static final double LAT_MAX = 39.0;
    private static final double LNG_MIN = 124.0;
    private static final double LNG_MAX = 132.0;

    public DeliveryLocation {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)
                || latitude < LAT_MIN || latitude > LAT_MAX
                || longitude < LNG_MIN || longitude > LNG_MAX) {
            throw new BusinessException(ErrorCode.DELIVERY_LOCATION_INVALID);
        }
        if (accuracyM != null && (accuracyM.isNaN() || accuracyM.isInfinite() || accuracyM < 0)) {
            throw new BusinessException(ErrorCode.DELIVERY_LOCATION_INVALID);
        }
    }
}
