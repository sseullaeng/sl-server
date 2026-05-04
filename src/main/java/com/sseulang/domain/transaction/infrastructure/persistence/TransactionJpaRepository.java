package com.sseulang.domain.transaction.infrastructure.persistence;

import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionStatus;
import com.sseulang.domain.transaction.domain.TransactionStatusCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Spring Data JPA — {@link TransactionRepositoryImpl} 가 wrapping. 외부에서 직접 import 금지. */
interface TransactionJpaRepository extends JpaRepository<Transaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") Long id);

    /**
     * status 별 거래 건수 집계. 단일 쿼리 — N+1 없음. 결과는 status 가 한 번이라도 등장한 행만 옴
     * (0 건인 status 는 application 단에서 0 으로 채움). status 컬럼 인덱스 권장.
     */
    @Query("""
            SELECT new com.sseulang.domain.transaction.domain.TransactionStatusCount(t.status, COUNT(t))
              FROM Transaction t
             GROUP BY t.status
            """)
    List<TransactionStatusCount> countGroupByStatusJpql();

    /**
     * Admin 거래 검색 (round 9 + round 10). created_at [start, end] + tradeType / status / keywordId / matchedUserIds.
     * 모두 nullable. 최신순.
     *
     * <p>keywordId: t.id 또는 t.itemId 정확 매칭 (숫자 keyword).</p>
     * <p>matchedUserIds: t.sellerId 또는 t.buyerId 가 IN (cross-aggregate user LIKE 매치 결과).</p>
     * <p>두 파라미터는 호출자가 mutually exclusive 로 채움 — 동시에 쓰면 OR 로 합쳐짐.</p>
     */
    @Query("""
            SELECT t FROM Transaction t
             WHERE (:start IS NULL OR t.createdAt >= :start)
               AND (:end   IS NULL OR t.createdAt <= :end)
               AND (:tradeType IS NULL OR t.tradeType = :tradeType)
               AND (:status    IS NULL OR t.status    = :status)
               AND (
                    (:keywordId IS NULL AND :hasUserIds = FALSE)
                    OR (:keywordId IS NOT NULL AND (t.id = :keywordId OR t.itemId = :keywordId))
                    OR (:hasUserIds = TRUE AND (t.sellerId IN :userIds OR t.buyerId IN :userIds))
               )
             ORDER BY t.id DESC
            """)
    Page<Transaction> adminSearchJpql(
            @Param("start") java.time.LocalDateTime start,
            @Param("end")   java.time.LocalDateTime end,
            @Param("tradeType") com.sseulang.domain.item.domain.TradeType tradeType,
            @Param("status")    TransactionStatus status,
            @Param("keywordId") Long keywordId,
            @Param("hasUserIds") boolean hasUserIds,
            @Param("userIds")    java.util.Collection<Long> userIds,
            Pageable pageable
    );

    /** Admin 회원 카드용 — sellerId 로 참여한 거래 (sellerId, count). */
    @Query("""
            SELECT t.sellerId AS userId, COUNT(t) AS cnt FROM Transaction t
             WHERE t.sellerId IN :ids
             GROUP BY t.sellerId
            """)
    List<UserCountRow> countAsSellerRaw(@Param("ids") java.util.Collection<Long> ids);

    /** Admin 회원 카드용 — buyerId 로 참여한 거래 (buyerId, count). */
    @Query("""
            SELECT t.buyerId AS userId, COUNT(t) AS cnt FROM Transaction t
             WHERE t.buyerId IN :ids
             GROUP BY t.buyerId
            """)
    List<UserCountRow> countAsBuyerRaw(@Param("ids") java.util.Collection<Long> ids);

    interface UserCountRow {
        Long getUserId();
        Long getCnt();
    }

    /**
     * 월별 거래완료 집계 — completed_at YEAR/MONTH GROUP BY. native MySQL.
     * Result projection: (year, month, count, amount). YearMonth 변환은 호출자.
     */
    @Query(value = """
            SELECT YEAR(completed_at) AS y, MONTH(completed_at) AS m,
                   COUNT(*) AS cnt, COALESCE(SUM(price), 0) AS amt
              FROM transactions
             WHERE status = '거래완료'
               AND completed_at >= :fromTs
               AND completed_at <  :toTs
             GROUP BY YEAR(completed_at), MONTH(completed_at)
             ORDER BY y ASC, m ASC
            """, nativeQuery = true)
    List<MonthlyStatRow> countCompletedMonthlyRaw(
            @Param("fromTs") java.time.LocalDateTime fromTs,
            @Param("toTs") java.time.LocalDateTime toTs
    );

    interface MonthlyStatRow {
        Integer getY();
        Integer getM();
        Long getCnt();
        Long getAmt();
    }

    /**
     * Review 작성 대기 거래 — completedAt 하한 + 본인 참여 + 본인이 reviewer 인 review 가 아직 없는 것.
     * Review 와 NOT EXISTS 로 cross-aggregate read (write 가 아니라 도메인 invariant 영향 X).
     */
    @Query(value = """
            SELECT t FROM Transaction t
             WHERE t.status = com.sseulang.domain.transaction.domain.TransactionStatus.거래완료
               AND t.completedAt >= :since
               AND (t.sellerId = :userId OR t.buyerId = :userId)
               AND NOT EXISTS (
                   SELECT 1 FROM com.sseulang.domain.review.domain.Review r
                    WHERE r.transactionId = t.id
                      AND r.reviewerId = :userId
               )
             ORDER BY t.completedAt DESC
            """,
            countQuery = """
            SELECT COUNT(t) FROM Transaction t
             WHERE t.status = com.sseulang.domain.transaction.domain.TransactionStatus.거래완료
               AND t.completedAt >= :since
               AND (t.sellerId = :userId OR t.buyerId = :userId)
               AND NOT EXISTS (
                   SELECT 1 FROM com.sseulang.domain.review.domain.Review r
                    WHERE r.transactionId = t.id
                      AND r.reviewerId = :userId
               )
            """)
    Page<Transaction> findPendingReviewableJpql(@Param("userId") Long userId,
                                                 @Param("since") LocalDateTime since,
                                                 Pageable pageable);

    // ───────── 내 거래 목록 (마이페이지) ─────────
    // role 4 분기 × status null/!=null 2 분기 = 6 메서드. role/status 모두 null 이 가장 흔한 케이스.

    @Query("""
            SELECT t FROM Transaction t
             WHERE (t.sellerId = :userId OR t.buyerId = :userId)
             ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<Transaction> findByParticipant(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
             WHERE (t.sellerId = :userId OR t.buyerId = :userId) AND t.status = :status
             ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<Transaction> findByParticipantAndStatus(@Param("userId") Long userId,
                                                  @Param("status") TransactionStatus status,
                                                  Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
             WHERE t.buyerId = :userId
             ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<Transaction> findByBuyer(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
             WHERE t.buyerId = :userId AND t.status = :status
             ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<Transaction> findByBuyerAndStatus(@Param("userId") Long userId,
                                            @Param("status") TransactionStatus status,
                                            Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
             WHERE t.sellerId = :userId
             ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<Transaction> findBySeller(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
             WHERE t.sellerId = :userId AND t.status = :status
             ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<Transaction> findBySellerAndStatus(@Param("userId") Long userId,
                                             @Param("status") TransactionStatus status,
                                             Pageable pageable);
}
