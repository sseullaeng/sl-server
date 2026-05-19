package com.sseulang.domain.payment.infrastructure.persistence;

import com.sseulang.domain.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByMerchantUid(String merchantUid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.merchantUid = :merchantUid")
    Optional<Payment> findByMerchantUidForUpdate(@Param("merchantUid") String merchantUid);

    

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = com.sseulang.domain.payment.domain.PaymentStatus.완료")
    long sumPaidAmount();

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = com.sseulang.domain.payment.domain.PaymentStatus.완료")
    long countPaid();

    

    @Query("""
            SELECT p FROM Payment p
             WHERE p.status = com.sseulang.domain.payment.domain.PaymentStatus.대기
               AND p.createdAt < :cutoff
             ORDER BY p.createdAt ASC
            """)
    List<Payment> findStalePending(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
