package com.sseulang.domain.transaction.domain;

import com.sseulang.domain.transaction.application.dto.TransactionRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
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

    // ───────── Review pending (follow-up #56) ─────────

    /**
     * 사용자가 reviewer 로 아직 작성하지 않은 거래완료 transaction 목록 (페이징, completedAt DESC).
     *
     * <p>cross-aggregate read query — Review 와 NOT EXISTS 로 join. write flow 는 여전히
     * 각 aggregate root 가 책임. 본 메서드는 read-only 라 도메인 invariant 를 깨지 않음.</p>
     *
     * @param userId    조회 주체 (seller 또는 buyer 중 하나로 참여)
     * @param since     completedAt 하한 (보통 now - 7일) — 작성 가능 기간 밖은 제외
     */
    Page<Transaction> findPendingReviewable(Long userId, LocalDateTime since, Pageable pageable);

    /**
     * 마이페이지 거래 목록 — viewer 가 buyer/seller/양쪽 으로 참여한 거래 페이징.
     *
     * @param userId 조회 주체
     * @param role   {@link TransactionRole#BUYER} = buyer 만, {@link TransactionRole#SELLER} = seller 만, null = 양쪽
     * @param status null = 전체 (취소 포함), 명시 시 정확 일치
     */
    Page<Transaction> findMyTransactions(
            Long userId, TransactionRole role, TransactionStatus status, Pageable pageable);
}
