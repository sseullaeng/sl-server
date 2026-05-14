package com.sseulang.domain.user.infrastructure.persistence;

import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findBySocialProviderAndSocialId(SocialProvider provider, String socialId);

    Optional<User> findByEmail(String email);

    

    @Modifying
    @Query(value = """
        UPDATE users
        SET review_count = review_count + 1,
            rating_sum = rating_sum + :rating,
            trust_score = CAST((rating_sum + :rating) AS DECIMAL(10,4)) / (review_count + 1)
        WHERE id = :userId
    """, nativeQuery = true)
    int recordReviewFor(@Param("userId") Long userId, @Param("rating") int rating);

    

    @Modifying
    @Query("UPDATE User u SET u.pointBalance = u.pointBalance + :amount WHERE u.id = :userId")
    int creditPointBalance(@Param("userId") Long userId, @Param("amount") long amount);

    

    @Modifying
    @Query("UPDATE User u SET u.pointBalance = u.pointBalance - :amount WHERE u.id = :userId AND u.pointBalance >= :amount")
    int deductPointBalance(@Param("userId") Long userId, @Param("amount") long amount);

    

    @Query("SELECT u.pointBalance FROM User u WHERE u.id = :userId")
    Long findPointBalanceById(@Param("userId") Long userId);

    

    @Modifying
    @Query("""
            UPDATE User u
               SET u.pointBalance = u.pointBalance - :amount,
                   u.pointHold    = u.pointHold + :amount
             WHERE u.id = :userId
               AND u.pointBalance >= :amount
            """)
    int holdForEscrow(@Param("userId") Long userId, @Param("amount") long amount);

    

    @Modifying
    @Query("UPDATE User u SET u.pointHold = u.pointHold - :amount WHERE u.id = :userId AND u.pointHold >= :amount")
    int releaseHold(@Param("userId") Long userId, @Param("amount") long amount);

    

    @Modifying
    @Query("""
            UPDATE User u
               SET u.pointBalance = u.pointBalance + :amount,
                   u.pointHold    = u.pointHold - :amount
             WHERE u.id = :userId
               AND u.pointHold >= :amount
            """)
    int refundHold(@Param("userId") Long userId, @Param("amount") long amount);

    
    @Query("SELECT u.pointHold FROM User u WHERE u.id = :userId")
    Long findPointHoldById(@Param("userId") Long userId);

    

    @Query(value = "SELECT point_balance AS balance, point_hold AS hold FROM users WHERE id = :userId",
           nativeQuery = true)
    java.util.Optional<PointSnapshotRow> findPointSnapshotById(@Param("userId") Long userId);

    
    interface PointSnapshotRow {
        long getBalance();
        long getHold();
    }

    Page<User> findAllByOrderByIdDesc(Pageable pageable);

    

    @Query(value = """
            SELECT * FROM users u
             WHERE (:kw IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :kw, '%'))
                                 OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :kw, '%')))
               AND (:after  IS NULL OR u.created_at >= :after)
               AND (:before IS NULL OR u.created_at <= :before)
               AND (:status IS NULL
                    OR (:status = 'WITHDRAWN' AND u.is_deleted = TRUE)
                    OR (:status = 'BLOCKED'   AND u.is_deleted = FALSE
                                              AND u.is_blocked = TRUE)
                    OR (:status = 'SUSPENDED' AND u.is_deleted = FALSE
                                              AND u.is_blocked = FALSE
                                              AND u.suspended_at IS NOT NULL
                                              AND u.suspend_days IS NOT NULL
                                              AND u.suspend_days > 0
                                              AND DATE_ADD(u.suspended_at, INTERVAL u.suspend_days DAY) > :now)
                    OR (:status = 'DORMANT'   AND u.is_deleted = FALSE
                                              AND u.is_blocked = FALSE
                                              AND (u.suspended_at IS NULL
                                                   OR u.suspend_days IS NULL
                                                   OR u.suspend_days <= 0
                                                   OR DATE_ADD(u.suspended_at, INTERVAL u.suspend_days DAY) <= :now)
                                              AND COALESCE(u.last_login_at, u.created_at) <= :dormantThreshold)
                    OR (:status = 'ACTIVE'    AND u.is_deleted = FALSE
                                              AND u.is_blocked = FALSE
                                              AND (u.suspended_at IS NULL
                                                   OR u.suspend_days IS NULL
                                                   OR u.suspend_days <= 0
                                                   OR DATE_ADD(u.suspended_at, INTERVAL u.suspend_days DAY) <= :now)
                                              AND COALESCE(u.last_login_at, u.created_at) > :dormantThreshold))
             ORDER BY u.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM users u
             WHERE (:kw IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :kw, '%'))
                                 OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :kw, '%')))
               AND (:after  IS NULL OR u.created_at >= :after)
               AND (:before IS NULL OR u.created_at <= :before)
               AND (:status IS NULL
                    OR (:status = 'WITHDRAWN' AND u.is_deleted = TRUE)
                    OR (:status = 'BLOCKED'   AND u.is_deleted = FALSE
                                              AND u.is_blocked = TRUE)
                    OR (:status = 'SUSPENDED' AND u.is_deleted = FALSE
                                              AND u.is_blocked = FALSE
                                              AND u.suspended_at IS NOT NULL
                                              AND u.suspend_days IS NOT NULL
                                              AND u.suspend_days > 0
                                              AND DATE_ADD(u.suspended_at, INTERVAL u.suspend_days DAY) > :now)
                    OR (:status = 'DORMANT'   AND u.is_deleted = FALSE
                                              AND u.is_blocked = FALSE
                                              AND (u.suspended_at IS NULL
                                                   OR u.suspend_days IS NULL
                                                   OR u.suspend_days <= 0
                                                   OR DATE_ADD(u.suspended_at, INTERVAL u.suspend_days DAY) <= :now)
                                              AND COALESCE(u.last_login_at, u.created_at) <= :dormantThreshold)
                    OR (:status = 'ACTIVE'    AND u.is_deleted = FALSE
                                              AND u.is_blocked = FALSE
                                              AND (u.suspended_at IS NULL
                                                   OR u.suspend_days IS NULL
                                                   OR u.suspend_days <= 0
                                                   OR DATE_ADD(u.suspended_at, INTERVAL u.suspend_days DAY) <= :now)
                                              AND COALESCE(u.last_login_at, u.created_at) > :dormantThreshold))
            """,
            nativeQuery = true)
    Page<User> searchAdmin(
            @Param("kw") String kw,
            @Param("after") java.time.LocalDateTime after,
            @Param("before") java.time.LocalDateTime before,
            @Param("status") String status,
            @Param("dormantThreshold") java.time.LocalDateTime dormantThreshold,
            @Param("now") java.time.LocalDateTime now,
            Pageable pageable
    );

    @Query("SELECT COUNT(u) FROM User u WHERE u.blocked = true")
    long countBlocked();

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from AND u.createdAt < :to")
    long countSignupsBetween(@Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to);

    

    @Query(value = """
            SELECT DATE(u.created_at) AS d, COUNT(*) AS c
              FROM users u
             WHERE u.created_at >= :from AND u.created_at < :to
             GROUP BY DATE(u.created_at)
             ORDER BY d ASC
            """, nativeQuery = true)
    java.util.List<DailySignupRow> findDailySignupsRaw(
            @Param("from") java.time.LocalDateTime from,
            @Param("to") java.time.LocalDateTime to);

    
    interface DailySignupRow {
        java.sql.Date getD();
        long getC();
    }

    @Query("SELECT COUNT(u) FROM User u WHERE u.deleted = true")
    long countDeleted();

    

    @Query("SELECT COUNT(u) FROM User u WHERE u.blocked = false AND u.deleted = false")
    long countActive();

    
    @Query("SELECT u.id FROM User u WHERE u.blocked = false AND u.deleted = false AND u.id > :afterId ORDER BY u.id ASC")
    java.util.List<Long> findActiveIdsAfter(@Param("afterId") long afterId, org.springframework.data.domain.Pageable pageable);

    
    @Query("""
            SELECT u.id FROM User u
             WHERE LOWER(u.email)    LIKE LOWER(CONCAT('%', :kw, '%'))
                OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :kw, '%'))
             ORDER BY u.id ASC
            """)
    java.util.List<Long> findIdsByKeywordLike(@Param("kw") String kw, org.springframework.data.domain.Pageable pageable);

    

    @Query("""
            SELECT u.id FROM User u
             WHERE u.cumulativeSuspendDays >= :threshold
               AND u.deleted = false
             ORDER BY u.id ASC
            """)
    java.util.List<Long> findAutoWithdrawTargetIds(@Param("threshold") int threshold, org.springframework.data.domain.Pageable pageable);
}
