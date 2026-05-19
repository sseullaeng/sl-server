package com.sseulang.domain.overdue.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface OverdueRecordRepository {

    boolean existsByEscrowApplicationId(Long escrowApplicationId);

    Optional<OverdueRecord> findById(Long id);

    Optional<OverdueRecord> findByIdForUpdate(Long id);

    Optional<OverdueRecord> findByEscrowApplicationId(Long escrowApplicationId);

    Optional<OverdueRecord> findByEscrowApplicationIdForUpdate(Long escrowApplicationId);

    List<Long> findActiveIds(int limit);

    Page<OverdueRecord> searchAdmin(OverdueStatus status, OverduePhase phase, Pageable pageable);

    List<OverdueRecord> findByBuyerIdAndStatusIn(Long buyerId, List<OverdueStatus> statuses);

    OverdueRecord save(OverdueRecord record);
}
