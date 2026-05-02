package com.sseulang.domain.chat.application;

import com.sseulang.domain.chat.domain.ItemView;

import java.util.Collection;
import java.util.Map;

/** 단위 테스트용 fake — 항상 빈 맵 반환. itemTitle/thumbnailUrl 는 null. */
public class NoOpItemView implements ItemView {
    @Override
    public Map<Long, ItemProjection> findByIds(Collection<Long> itemIds) {
        return Map.of();
    }
}
