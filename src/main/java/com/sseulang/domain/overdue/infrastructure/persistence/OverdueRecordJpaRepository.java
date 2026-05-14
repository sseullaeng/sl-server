package com.sseulang.domain.overdue.infrastructure.persistence;

import com.sseulang.domain.overdue.domain.OverdueRecord;
import com.sseulang.domain.overdue.domain.OverdueStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OverdueRecordJpaRepository extends JpaRepository<OverdueRecord, Long> {

    boolean existsByEscrowApplicationId(Long escrowApplicationId);

    Optional<OverdueRecord> findByEscrowApplicationId(Long escrowApplicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM OverdueRecord r WHERE r.id = :id")
    Optional<OverdueRecord> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM OverdueRecord r WHERE r.escrowApplicationId = :escrowApplicationId")
    Optional<OverdueRecord> findByEscrowApplicationIdForUpdate(@Param("escrowApplicationId") Long escrowApplicationId);

    @Query("""
            SELECT r.id FROM OverdueRecord r
             WHERE r.status = :status
             ORDER BY r.id ASC
            """)
    List<Long> findActiveIds(@Param("status") OverdueStatus status, Pageable pageable);
}
