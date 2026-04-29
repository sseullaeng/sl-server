package com.sseulang.domain.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findById(Long id);

    Optional<Payment> findByMerchantUid(String merchantUid);

    /** 비관적 락 — confirm/webhook race 직렬화 (가이드 §5.3 동시성). */
    Optional<Payment> findByMerchantUidForUpdate(String merchantUid);

    Payment save(Payment payment);
}
