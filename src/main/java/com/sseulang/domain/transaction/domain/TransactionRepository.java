package com.sseulang.domain.transaction.domain;

import java.util.List;
import java.util.Optional;

/**
 * Transaction Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 */
public interface TransactionRepository {

    Optional<Transaction> findById(Long id);

    /**
     * 비관적 쓰기 락(PESSIMISTIC_WRITE)으로 Transaction 조회. 같은 거래에 대한 동시 reserve/cancel/complete
     * race 직렬화 — Codex 게이트 1 보강 (Critical: tx 행 자체에 락이 없어 reserve vs cancel 가
     * Transaction=취소, Item=예약 불일치를 만들 수 있던 케이스 차단).
     *
     * <p>락 순서: <b>Transaction → Item</b>. 모든 write flow 가 동일 순서를 지켜 deadlock 회피.</p>
     */
    Optional<Transaction> findByIdForUpdate(Long id);

    Transaction save(Transaction transaction);

    // ───────── 관리자 통계 ─────────

    /** 단일 GROUP BY 집계 — (status, count) 행 리스트 (가능한 모든 status 행 포함, 0 인 status 는 없음). */
    List<TransactionStatusCount> countGroupByStatus();
}
