package com.sseulang.domain.point.application;

import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryRepository;
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

    public List<PointHistory> all() {
        return List.copyOf(store);
    }

    public int size() {
        return store.size();
    }
}
