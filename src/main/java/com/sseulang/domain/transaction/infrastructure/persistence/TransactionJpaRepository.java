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

interface TransactionJpaRepository extends JpaRepository<Transaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") Long id);

    

    @Query("""
            SELECT (COUNT(t) > 0) FROM Transaction t
             WHERE t.chatRoomId = :chatRoomId
               AND t.status NOT IN (
                   com.sseulang.domain.transaction.domain.TransactionStatus.거래완료,
                   com.sseulang.domain.transaction.domain.TransactionStatus.취소
               )
            """)
    boolean existsActiveByChatRoomIdJpql(@Param("chatRoomId") Long chatRoomId);

    // 라운드 12 — admin item 상세 거래 이력.
    List<Transaction> findByItemIdOrderByIdDesc(Long itemId);

    // 라운드 12 — 거래대행 paired Transaction (1:1).
    Optional<Transaction> findByEscrowApplicationId(Long escrowApplicationId);

    List<Transaction> findByEscrowApplicationIdIn(java.util.Collection<Long> escrowApplicationIds);

    // 라운드 12 — 채팅방 카드용. 비취소 최신 1건 (id desc).
    @Query("""
            SELECT t FROM Transaction t
             WHERE t.chatRoomId = :chatRoomId
               AND t.status <> com.sseulang.domain.transaction.domain.TransactionStatus.취소
             ORDER BY t.id DESC
            """)
    List<Transaction> findLatestNonCanceledByChatRoomIdJpql(@Param("chatRoomId") Long chatRoomId, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
             WHERE t.chatRoomId IN :chatRoomIds
               AND t.status <> com.sseulang.domain.transaction.domain.TransactionStatus.취소
            """)
    List<Transaction> findNonCanceledByChatRoomIdInJpql(@Param("chatRoomIds") java.util.Collection<Long> chatRoomIds);

    

    @Query("""
            SELECT new com.sseulang.domain.transaction.domain.TransactionStatusCount(t.status, COUNT(t))
              FROM Transaction t
             GROUP BY t.status
            """)
    List<TransactionStatusCount> countGroupByStatusJpql();

    
    @Query("""
            SELECT new com.sseulang.domain.transaction.domain.TransactionRepository$TradeTypeCount(t.tradeType, COUNT(t))
              FROM Transaction t
             WHERE t.createdAt >= :from AND t.createdAt < :to
             GROUP BY t.tradeType
            """)
    List<com.sseulang.domain.transaction.domain.TransactionRepository.TradeTypeCount> countByTradeTypeBetweenJpql(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    
    @Query("""
            SELECT new com.sseulang.domain.transaction.domain.TransactionStatusCount(t.status, COUNT(t))
              FROM Transaction t
             WHERE t.createdAt >= :from AND t.createdAt < :to
             GROUP BY t.status
            """)
    List<TransactionStatusCount> countByStatusBetweenJpql(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    

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

    
    @Query("""
            SELECT t.sellerId AS userId, COUNT(t) AS cnt FROM Transaction t
             WHERE t.sellerId IN :ids
             GROUP BY t.sellerId
            """)
    List<UserCountRow> countAsSellerRaw(@Param("ids") java.util.Collection<Long> ids);

    
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

    @Query("""
            SELECT t FROM Transaction t
             WHERE (t.sellerId = :userId OR t.buyerId = :userId) AND t.status IN :statuses
             ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<Transaction> findByParticipantAndStatusIn(@Param("userId") Long userId,
                                                    @Param("statuses") java.util.Collection<TransactionStatus> statuses,
                                                    Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
             WHERE t.buyerId = :userId AND t.status IN :statuses
             ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<Transaction> findByBuyerAndStatusIn(@Param("userId") Long userId,
                                              @Param("statuses") java.util.Collection<TransactionStatus> statuses,
                                              Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
             WHERE t.sellerId = :userId AND t.status IN :statuses
             ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<Transaction> findBySellerAndStatusIn(@Param("userId") Long userId,
                                               @Param("statuses") java.util.Collection<TransactionStatus> statuses,
                                               Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
             WHERE t.itemId = :itemId
               AND t.tradeType = com.sseulang.domain.item.domain.TradeType.대여
               AND t.status NOT IN (
                   com.sseulang.domain.transaction.domain.TransactionStatus.취소,
                   com.sseulang.domain.transaction.domain.TransactionStatus.거래완료
               )
               AND t.rentalStart IS NOT NULL AND t.rentalEnd IS NOT NULL
             ORDER BY t.rentalStart ASC
            """)
    java.util.List<Transaction> findActiveRentalsByItemIdJpql(@Param("itemId") Long itemId);
}
