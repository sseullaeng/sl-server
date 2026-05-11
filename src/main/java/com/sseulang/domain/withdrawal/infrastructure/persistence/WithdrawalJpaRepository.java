package com.sseulang.domain.withdrawal.infrastructure.persistence;

import com.sseulang.domain.withdrawal.domain.Withdrawal;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatusCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface WithdrawalJpaRepository extends JpaRepository<Withdrawal, Long> {

    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Withdrawal w WHERE w.id = :id")
    Optional<Withdrawal> findByIdForUpdate(@Param("id") Long id);

    

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Withdrawal w WHERE w.id = :id AND w.userId = :userId")
    Optional<Withdrawal> findByIdAndUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    Optional<Withdrawal> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Page<Withdrawal> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Withdrawal> findByStatusOrderByCreatedAtDesc(WithdrawalStatus status, Pageable pageable);

    Page<Withdrawal> findAllByOrderByCreatedAtDesc(Pageable pageable);

    

    @Query("""
            SELECT new com.sseulang.domain.withdrawal.domain.WithdrawalStatusCount(w.status, COUNT(w))
              FROM Withdrawal w
             GROUP BY w.status
            """)
    List<WithdrawalStatusCount> countGroupByStatusJpql();

    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM Withdrawal w WHERE w.status = com.sseulang.domain.withdrawal.domain.WithdrawalStatus.완료")
    long sumCompletedAmount();
}
