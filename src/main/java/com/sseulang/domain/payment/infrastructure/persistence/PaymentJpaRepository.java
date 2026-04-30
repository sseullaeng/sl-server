package com.sseulang.domain.payment.infrastructure.persistence;

import com.sseulang.domain.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByMerchantUid(String merchantUid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.merchantUid = :merchantUid")
    Optional<Payment> findByMerchantUidForUpdate(@Param("merchantUid") String merchantUid);

    /**
     * status=완료 결제의 amount 합계. COALESCE 로 빈 결과(완료 0건)에서도 0 반환 — Long null 회피.
     * payments.status 인덱스 권장 (V1 schema 에 idx_payments_status 있음).
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = com.sseulang.domain.payment.domain.PaymentStatus.완료")
    long sumPaidAmount();

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = com.sseulang.domain.payment.domain.PaymentStatus.완료")
    long countPaid();
}
