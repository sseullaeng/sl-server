package com.sseulang.domain.delivery.presentation.dto;

import com.sseulang.domain.delivery.application.dto.AdminDeliveryStatsResult;
import com.sseulang.domain.delivery.domain.DeliveryStatus;

import java.util.LinkedHashMap;
import java.util.Map;

public record AdminDeliveryStatsResponse(
        Map<String, Long> byStatus,
        long total,
        long todayNew
) {
    public static AdminDeliveryStatsResponse from(AdminDeliveryStatsResult r) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map.Entry<DeliveryStatus, Long> e : r.byStatus().entrySet()) {
            map.put(e.getKey().name(), e.getValue());
        }
        return new AdminDeliveryStatsResponse(map, r.total(), r.todayNew());
    }
}
