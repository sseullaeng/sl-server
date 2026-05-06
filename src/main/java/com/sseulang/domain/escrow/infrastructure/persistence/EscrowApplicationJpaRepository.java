package com.sseulang.domain.escrow.infrastructure.persistence;

import com.sseulang.domain.escrow.domain.EscrowApplication;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EscrowApplicationJpaRepository extends JpaRepository<EscrowApplication, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM EscrowApplication a WHERE a.id = :id")
    Optional<EscrowApplication> findByIdForUpdate(@Param("id") Long id);

    Optional<EscrowApplication> findByLinkId(Long linkId);

    @Query("""
            SELECT a FROM EscrowApplication a
            WHERE a.initiatorId = :userId OR a.receiverId = :userId
            """)
    Page<EscrowApplication> findMyApplications(@Param("userId") Long userId, Pageable pageable);

    Page<EscrowApplication> findAllByStatus(EscrowApplicationStatus status, Pageable pageable);

    @Query("""
            SELECT a FROM EscrowApplication a
            WHERE a.status = com.sseulang.domain.escrow.domain.EscrowApplicationStatus.결제대기
              AND a.paymentDueAt IS NOT NULL
              AND a.paymentDueAt < :now
            """)
    List<EscrowApplication> findPaymentTimedOut(@Param("now") LocalDateTime now);

    @Query("""
            SELECT COUNT(a) FROM EscrowApplication a
            WHERE a.status IN (
                com.sseulang.domain.escrow.domain.EscrowApplicationStatus.결제대기,
                com.sseulang.domain.escrow.domain.EscrowApplicationStatus.결제완료,
                com.sseulang.domain.escrow.domain.EscrowApplicationStatus.진행중
            )
            """)
    long countInProgress();
}
