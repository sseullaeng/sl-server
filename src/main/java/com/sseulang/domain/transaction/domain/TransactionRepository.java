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

    /**
     * Admin 회원 카드용 — userId 가 buyer 또는 seller 로 참여한 transaction 의 (userId → count) 맵.
     * 빈 입력은 빈 맵. 단일 GROUP BY 쿼리 — N+1 회피.
     */
    java.util.Map<Long, Long> countByUserIdsAsParticipant(java.util.Collection<Long> userIds);

    /**
     * 월별 거래 집계 — completed_at 기준 (status=거래완료 만). [from, to] 월 범위 inclusive.
     * 결과는 월별 (year, month, count, sumPrice). 거래 0건 월은 응답에 포함되지 않음 (호출자가 0 채움).
     */
    List<TransactionMonthlyStat> countCompletedMonthly(java.time.YearMonth from, java.time.YearMonth to);

    /**
     * Admin 거래 검색 (round 9 + round 10 LIKE 보강). created_at [start, end] + tradeType / status + keyword.
     *
     * <p>keyword 처리 정책 (호출자 ApplicationService 책임):
     * <ul>
     *   <li>숫자 → transactionId 또는 itemId 정확 매칭</li>
     *   <li>비숫자 → ApplicationService 가 UserApplicationService.findUserIdsByKeyword 로 변환 후
     *       {@code matchedUserIds} 로 전달 (Transaction.sellerId/buyerId IN). 빈 매치는 빈 결과.</li>
     * </ul>
     * 모든 필터 nullable. 최신순.</p>
     *
     * @param matchedUserIds 비숫자 keyword 의 user 매치 결과. null 또는 empty 면 user 필터 미적용.
     */
    org.springframework.data.domain.Page<Transaction> adminSearch(
            java.time.LocalDateTime startDate,
            java.time.LocalDateTime endDate,
            com.sseulang.domain.item.domain.TradeType tradeType,
            TransactionStatus status,
            String keyword,
            java.util.Collection<Long> matchedUserIds,
            org.springframework.data.domain.Pageable pageable
    );

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
