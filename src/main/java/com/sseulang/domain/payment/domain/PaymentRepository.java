package com.sseulang.domain.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findById(Long id);

    Optional<Payment> findByMerchantUid(String merchantUid);

    /** 비관적 락 — confirm/webhook race 직렬화 (가이드 §5.3 동시성). */
    Optional<Payment> findByMerchantUidForUpdate(String merchantUid);

    Payment save(Payment payment);

    // ───────── 관리자 통계 ─────────

    /** status=완료 결제의 paid_amount 합계. 미완료 결제는 제외. */
    long sumPaidAmount();

    /** status=완료 결제 건수. */
    long countPaid();
}
