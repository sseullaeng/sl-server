package com.sseulang.domain.item.application;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemRepository;
import com.sseulang.domain.item.domain.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 테스트용 인메모리 fake. id 자동 부여 + status != 삭제 인 것만 반환. */
public class InMemoryFakeItemRepository implements ItemRepository {

    private final Map<Long, Item> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Optional<Item> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Page<Item> findVisibleLatest(Pageable pageable) {
        List<Item> visible = store.values().stream()
                .filter(i -> i.getStatus() != ItemStatus.삭제)
                .sorted(Comparator.comparingLong(Item::getId).reversed())
                .toList();
        int start = Math.min((int) pageable.getOffset(), visible.size());
        int end = Math.min(start + pageable.getPageSize(), visible.size());
        return new PageImpl<>(visible.subList(start, end), pageable, visible.size());
    }

    @Override
    public Item save(Item item) {
        if (item.getId() == null) {
            long id = ++sequence;
            ReflectionTestUtils.setField(item, "id", id);
        }
        store.put(item.getId(), item);
        return item;
    }

    @Override
    public void delete(Item item) {
        store.remove(item.getId());
    }
}
