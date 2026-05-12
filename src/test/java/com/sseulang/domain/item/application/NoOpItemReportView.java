package com.sseulang.domain.item.application;

import com.sseulang.domain.item.domain.ItemReportView;

import java.util.Collection;
import java.util.Map;

public class NoOpItemReportView implements ItemReportView {
    @Override
    public Map<Long, Long> countByItemIds(Collection<Long> itemIds) {
        return Map.of();
    }
}
