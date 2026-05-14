package com.sseulang.domain.overdue.domain;

import java.util.Optional;

public interface OverdueRecordRepository {

    boolean existsByEscrowApplicationId(Long escrowApplicationId);

    Optional<OverdueRecord> findById(Long id);

    Optional<OverdueRecord> findByEscrowApplicationId(Long escrowApplicationId);

    OverdueRecord save(OverdueRecord record);
}
