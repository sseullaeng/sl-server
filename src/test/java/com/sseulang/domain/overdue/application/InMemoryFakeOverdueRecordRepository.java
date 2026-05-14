package com.sseulang.domain.overdue.application;

import com.sseulang.domain.overdue.domain.OverdueRecord;
import com.sseulang.domain.overdue.domain.OverdueRecordRepository;
import com.sseulang.domain.overdue.domain.OverdueStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class InMemoryFakeOverdueRecordRepository implements OverdueRecordRepository {

    private final Map<Long, OverdueRecord> store = new LinkedHashMap<>();
    private long sequence = 0;

    @Override
    public boolean existsByEscrowApplicationId(Long escrowApplicationId) {
        return store.values().stream()
                .anyMatch(r -> escrowApplicationId.equals(r.getEscrowApplicationId()));
    }

    @Override
    public Optional<OverdueRecord> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<OverdueRecord> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    public Optional<OverdueRecord> findByEscrowApplicationId(Long escrowApplicationId) {
        return store.values().stream()
                .filter(r -> escrowApplicationId.equals(r.getEscrowApplicationId()))
                .findFirst();
    }

    @Override
    public Optional<OverdueRecord> findByEscrowApplicationIdForUpdate(Long escrowApplicationId) {
        return findByEscrowApplicationId(escrowApplicationId);
    }

    @Override
    public List<Long> findActiveIds(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return store.values().stream()
                .filter(r -> r.getStatus() == OverdueStatus.진행중)
                .map(OverdueRecord::getId)
                .sorted()
                .limit(limit)
                .toList();
    }

    @Override
    public OverdueRecord save(OverdueRecord record) {
        if (record.getId() == null) {
            ReflectionTestUtils.setField(record, "id", ++sequence);
        }
        store.put(record.getId(), record);
        return record;
    }

    List<OverdueRecord> all() {
        return store.values().stream()
                .sorted(Comparator.comparing(OverdueRecord::getId))
                .toList();
    }
}
