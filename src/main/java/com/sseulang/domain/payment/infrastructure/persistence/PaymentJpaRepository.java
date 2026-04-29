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
}
