package com.sseulang.domain.overdue.infrastructure.persistence;

import com.sseulang.domain.overdue.domain.OverdueRecord;
import com.sseulang.domain.overdue.domain.OverdueRecordRepository;
import org.springframework.stereotype.Repository;

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
    public Optional<OverdueRecord> findByEscrowApplicationId(Long escrowApplicationId) {
        return jpa.findByEscrowApplicationId(escrowApplicationId);
    }

    @Override
    public OverdueRecord save(OverdueRecord record) {
        return jpa.save(record);
    }
}
