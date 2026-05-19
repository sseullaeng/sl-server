package com.sseulang.domain.delivery.application.dto;

import com.sseulang.domain.delivery.domain.DeliveryStatus;

import java.util.Map;

public record AdminDeliveryStatsResult(
        Map<DeliveryStatus, Long> byStatus,
        long total,
        long todayNew
) {
}
