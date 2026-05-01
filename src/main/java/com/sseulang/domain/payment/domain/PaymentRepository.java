package com.sseulang.domain.payment.domain;

import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findById(Long id);

    Optional<Payment> findByMerchantUid(String merchantUid);

    /** 비관적 락 — confirm/webhook race 직렬화 (가이드 §5.3 동시성). */
    Optional<Payment> findByMerchantUidForUpdate(String merchantUid);

    Payment save(Payment payment);

    /**
     * dangling 복구 스케줄러용 (follow-up #21) — status=대기 + created_at < cutoff 인 Payment 목록.
     * confirm 도중 응답 유실 / 5xx 등으로 토스는 처리됐지만 우리 DB 는 대기 상태로 남은 결제를
     * 주기적으로 토스 lookup 으로 동기화.
     */
    java.util.List<Payment> findStalePending(java.time.LocalDateTime cutoff, int limit);

    // ───────── 관리자 통계 ─────────

    /** status=완료 결제의 paid_amount 합계. 미완료 결제는 제외. */
    long sumPaidAmount();

    /** status=완료 결제 건수. */
    long countPaid();
}
