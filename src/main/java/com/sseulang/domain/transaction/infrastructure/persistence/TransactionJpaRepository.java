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
