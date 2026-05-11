package com.sseulang.domain.item.infrastructure.persistence;

import com.sseulang.domain.chat.domain.ItemView;
import com.sseulang.domain.item.domain.Item;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class ItemViewAdapter implements ItemView {

    private final ItemJpaRepository jpa;

    public ItemViewAdapter(ItemJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Map<Long, ItemProjection> findByIds(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ItemProjection> result = new HashMap<>();
        for (Item i : jpa.findAllById(itemIds)) {
            result.put(i.getId(), new ItemProjection(i.getId(), i.getTitle(), i.getThumbnailUrl(), i.getSellerId(), i.getPrice()));
        }
        return result;
    }
}
