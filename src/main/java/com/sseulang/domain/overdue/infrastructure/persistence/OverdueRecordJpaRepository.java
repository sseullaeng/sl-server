package com.sseulang.domain.overdue.infrastructure.persistence;

import com.sseulang.domain.overdue.domain.OverdueRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OverdueRecordJpaRepository extends JpaRepository<OverdueRecord, Long> {

    boolean existsByEscrowApplicationId(Long escrowApplicationId);

    Optional<OverdueRecord> findByEscrowApplicationId(Long escrowApplicationId);
}
