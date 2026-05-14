package com.sseulang.domain.overdue.domain;

import java.util.Optional;

public interface OverdueRecordRepository {

    boolean existsByEscrowApplicationId(Long escrowApplicationId);

    Optional<OverdueRecord> findById(Long id);

    Optional<OverdueRecord> findByIdForUpdate(Long id);

    Optional<OverdueRecord> findByEscrowApplicationId(Long escrowApplicationId);

    Optional<OverdueRecord> findByEscrowApplicationIdForUpdate(Long escrowApplicationId);

    java.util.List<Long> findActiveIds(int limit);

    OverdueRecord save(OverdueRecord record);
}
