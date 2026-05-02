package com.sseulang.domain.point.application;

import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryRepository;
import com.sseulang.domain.point.domain.PointHistoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InMemoryFakePointHistoryRepository implements PointHistoryRepository {

    private final List<PointHistory> store = new ArrayList<>();
    private long sequence = 0;

    @Override
    public PointHistory save(PointHistory history) {
        if (history.getId() == null) {
            ReflectionTestUtils.setField(history, "id", ++sequence);
        }
        store.add(history);
        return history;
    }

    @Override
    public List<PointHistory> findByUserIdOrderByCreatedAtDesc(Long userId) {
        return store.stream()
                .filter(h -> userId.equals(h.getUserId()))
                .sorted(Comparator.comparing(PointHistory::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public Page<PointHistory> findByUserIdAndType(Long userId, PointHistoryType type, Pageable pageable) {
        List<PointHistory> filtered = store.stream()
                .filter(h -> userId.equals(h.getUserId()))
                .filter(h -> type == null || h.getPointType() == type)
                .sorted(Comparator.comparing(PointHistory::getCreatedAt).reversed()
                        .thenComparing(Comparator.comparingLong(PointHistory::getId).reversed()))
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    public List<PointHistory> all() {
        return List.copyOf(store);
    }

    public int size() {
        return store.size();
    }
}
