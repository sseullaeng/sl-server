package com.sseulang.domain.withdrawal.infrastructure.persistence;

import com.sseulang.domain.withdrawal.domain.Withdrawal;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Spring Data JPA — {@link WithdrawalRepositoryImpl} 가 wrapping. 외부 직접 import 금지. */
interface WithdrawalJpaRepository extends JpaRepository<Withdrawal, Long> {

    /** 가이드 §5.3 — 비관적 락 (SELECT FOR UPDATE) 으로 동시 승인/거부/취소 직렬화. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Withdrawal w WHERE w.id = :id")
    Optional<Withdrawal> findByIdForUpdate(@Param("id") Long id);

    /**
     * 본인 소유 + 락 동시 획득. 타인 id 로 시도 시 빈 결과 → 락 미획득 (cancel 의 lock-DoS 방지).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Withdrawal w WHERE w.id = :id AND w.userId = :userId")
    Optional<Withdrawal> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    Optional<Withdrawal> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Page<Withdrawal> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Withdrawal> findByStatusOrderByCreatedAtDesc(WithdrawalStatus status, Pageable pageable);

    Page<Withdrawal> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
