package com.sseulang.domain.overdue.infrastructure.persistence;

import com.sseulang.domain.overdue.domain.OverdueRecord;
import com.sseulang.domain.overdue.domain.OverdueRecordRepository;
import com.sseulang.domain.overdue.domain.OverdueStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OverdueRecordRepositoryImpl implements OverdueRecordRepository {

    private final OverdueRecordJpaRepository jpa;

    public OverdueRecordRepositoryImpl(OverdueRecordJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public boolean existsByEscrowApplicationId(Long escrowApplicationId) {
        return jpa.existsByEscrowApplicationId(escrowApplicationId);
    }

    @Override
    public Optional<OverdueRecord> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<OverdueRecord> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public Optional<OverdueRecord> findByEscrowApplicationId(Long escrowApplicationId) {
        return jpa.findByEscrowApplicationId(escrowApplicationId);
    }

    @Override
    public Optional<OverdueRecord> findByEscrowApplicationIdForUpdate(Long escrowApplicationId) {
        return jpa.findByEscrowApplicationIdForUpdate(escrowApplicationId);
    }

    @Override
    public List<Long> findActiveIds(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jpa.findActiveIds(OverdueStatus.진행중, PageRequest.of(0, limit));
    }

    @Override
    public OverdueRecord save(OverdueRecord record) {
        return jpa.save(record);
    }
}
