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

/** Spring Data JPA — {@link UserRepositoryImpl} 가 wrapping. 외부에서 직접 import 금지. */
interface UserJpaRepository extends JpaRepository<User, Long> {

    Optional<User> findBySocialProviderAndSocialId(SocialProvider provider, String socialId);

    Optional<User> findByEmail(String email);

    /**
     * 단일 atomic UPDATE — review_count / rating_sum 누적 + trust_score 즉시 재계산.
     * 같은 reviewee 에 대한 동시 review 작성 race 안전 (각 트랜잭션은 본인 행 락 점유 시 직렬화 + 누적).
     * 가이드 §5.3 동시성 처리 정합. Codex 게이트 2 보강.
     *
     * <p>trust_score 정밀도: DECIMAL(3,2) → CAST 로 분수 결과 보존.</p>
     */
    @Modifying
    @Query(value = """
        UPDATE users
        SET review_count = review_count + 1,
            rating_sum = rating_sum + :rating,
            trust_score = CAST((rating_sum + :rating) AS DECIMAL(10,4)) / (review_count + 1)
        WHERE id = :userId
    """, nativeQuery = true)
    int recordReviewFor(@Param("userId") Long userId, @Param("rating") int rating);

    /**
     * 가이드 §4.8 — point_balance 단일 atomic UPDATE 증가. 충전·정산 적립 시 호출.
     * 동시 충전 / 동시 적립 race 안전 (단일 SQL).
     */
    @Modifying
    @Query("UPDATE User u SET u.pointBalance = u.pointBalance + :amount WHERE u.id = :userId")
    int creditPointBalance(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 가이드 §4.8 / §5.3 — point_balance 단일 atomic UPDATE 차감. 잔액 부족 시 affected=0 (음수 방지 가드).
     * 거래 결제 (구매자 차감) / 출금 신청에서 호출. 동시 차감 race 안전 (행 락 직렬화).
     */
    @Modifying
    @Query("UPDATE User u SET u.pointBalance = u.pointBalance - :amount WHERE u.id = :userId AND u.pointBalance >= :amount")
    int deductPointBalance(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * point_balance scalar 조회. 영속성 컨텍스트의 stale entity 캐시 우회 — atomic UPDATE 직후
     * balance_after 적재용으로 호출 (Hibernate scalar projection 은 entity 캐시 거치지 않음).
     */
    @Query("SELECT u.pointBalance FROM User u WHERE u.id = :userId")
    Long findPointBalanceById(@Param("userId") Long userId);

    /**
     * 가이드 §5.1 거래 hold (라운드 11) — point_balance 차감 + point_hold 적립을 단일 원자 UPDATE.
     * 잔액 부족 시 affected=0 (WHERE point_balance >= :amount 가드 — 음수 방지).
     * 동시 hold race 안전 (행 락 직렬화 + 단일 SQL).
     *
     * <p><b>@Version 정책</b>: 본 메서드 + creditPointBalance / deductPointBalance / releaseHold /
     * refundHold / recordReviewFor 는 모두 가이드 §5.3 패턴 (원자 SQL 로 직접 컬럼 변경) — JPA dirty-check
     * 경로를 우회하므로 {@code @Version} 을 의도적으로 갱신하지 않는다. race-safety 는 SQL 자체의
     * 행 락 + WHERE 가드가 보장. 단, 같은 트랜잭션 안에서 다른 managed write (profile / block / suspend
     * 등 entity dirty-check 경로) 와 섞이면 별도 optimistic-lock 방어가 필요 — Day 5 takeover 패턴 참고.</p>
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.pointBalance = u.pointBalance - :amount,
                   u.pointHold    = u.pointHold + :amount
             WHERE u.id = :userId
               AND u.pointBalance >= :amount
            """)
    int holdForEscrow(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 거래완료 정산 — buyer point_hold 만 차감. seller credit 은 별도 호출 (양 사용자 id-asc 락 순서).
     * point_hold &lt; amount 면 affected=0 — 호출자가 fail-fast.
     */
    @Modifying
    @Query("UPDATE User u SET u.pointHold = u.pointHold - :amount WHERE u.id = :userId AND u.pointHold >= :amount")
    int releaseHold(@Param("userId") Long userId, @Param("amount") long amount);

    /**
     * 거래 취소 환불 — point_hold 차감 + point_balance 적립을 단일 원자 UPDATE.
     * point_hold &lt; amount 면 affected=0.
     */
    @Modifying
    @Query("""
            UPDATE User u
               SET u.pointBalance = u.pointBalance + :amount,
                   u.pointHold    = u.pointHold - :amount
             WHERE u.id = :userId
               AND u.pointHold >= :amount
            """)
    int refundHold(@Param("userId") Long userId, @Param("amount") long amount);

    /** point_hold scalar 조회. atomic UPDATE 직후 history balance_after 적재용. */
    @Query("SELECT u.pointHold FROM User u WHERE u.id = :userId")
    Long findPointHoldById(@Param("userId") Long userId);

    /**
     * 잔액 + hold 단일 SELECT 스냅샷 — 게이트 1 W-1 보강 (race-safe 일관성).
     * native projection 으로 영속성 컨텍스트 stale 우회.
     */
    @Query(value = "SELECT point_balance AS balance, point_hold AS hold FROM users WHERE id = :userId",
           nativeQuery = true)
    java.util.Optional<PointSnapshotRow> findPointSnapshotById(@Param("userId") Long userId);

    /** native projection — UserRepository.PointSnapshot 으로 매핑 (Spring Data 인터페이스 projection). */
    interface PointSnapshotRow {
        long getBalance();
        long getHold();
    }

    Page<User> findAllByOrderByIdDesc(Pageable pageable);

    /**
     * Admin 회원 검색 — keyword(nickname/email LIKE), created_at 범위, derive status.
     *
     * <p>NATIVE query 사용 이유: SUSPENDED 판정에 {@code suspended_at + INTERVAL suspend_days DAY > now}
     * 가 필요한데 JPQL TIMESTAMPADD 가 DB 별 호환성 이슈가 큼. MySQL DATE_ADD 직접 사용.</p>
     *
     * <p>status 파라미터:
     * <ul>
     *   <li>{@code 'WITHDRAWN'} — is_deleted=TRUE</li>
     *   <li>{@code 'SUSPENDED'} — 시한부 정지 만료 전</li>
     *   <li>{@code 'DORMANT'} — deleted/suspended 아니고 마지막 로그인이 dormantThreshold 이전</li>
     *   <li>{@code 'ACTIVE'} — 그 외 (탈퇴/정지/휴면 아님)</li>
     *   <li>{@code NULL} — 필터 안 함 (전체)</li>
     * </ul>
     */
    @Query(value = """
            SELECT * FROM users u
             WHERE (:kw IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :kw, '%'))
                                 OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :kw, '%')))
               AND (:after  IS NULL OR u.created_at >= :after)
               AND (:before IS NULL OR u.created_at <= :before)
               AND (:status IS NULL
                    OR (:status = 'WITHDRAWN' AND u.is_deleted = TRUE)
                    OR (:status = 'SUSPENDED' AND u.is_deleted = FALSE
                                              AND u.suspended_at IS NOT NULL
                                              AND u.suspend_days IS NOT NULL
                                              AND u.suspend_days > 0
                                              AND DATE_ADD(u.suspended_at, INTERVAL u.suspend_days DAY) > :now)
                    OR (:status = 'DORMANT'   AND u.is_deleted = FALSE
                                              AND (u.suspended_at IS NULL
                                                   OR u.suspend_days IS NULL
                                                   OR u.suspend_days <= 0
                                                   OR DATE_ADD(u.suspended_at, INTERVAL u.suspend_days DAY) <= :now)
                                              AND COALESCE(u.last_login_at, u.created_at) <= :dormantThreshold)
                    OR (:status = 'ACTIVE'    AND u.is_deleted = FALSE
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
                    OR (:status = 'SUSPENDED' AND u.is_deleted = FALSE
                                              AND u.suspended_at IS NOT NULL
                                              AND u.suspend_days IS NOT NULL
                                              AND u.suspend_days > 0
                                              AND DATE_ADD(u.suspended_at, INTERVAL u.suspend_days DAY) > :now)
                    OR (:status = 'DORMANT'   AND u.is_deleted = FALSE
                                              AND (u.suspended_at IS NULL
                                                   OR u.suspend_days IS NULL
                                                   OR u.suspend_days <= 0
                                                   OR DATE_ADD(u.suspended_at, INTERVAL u.suspend_days DAY) <= :now)
                                              AND COALESCE(u.last_login_at, u.created_at) <= :dormantThreshold)
                    OR (:status = 'ACTIVE'    AND u.is_deleted = FALSE
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

    @Query("SELECT COUNT(u) FROM User u WHERE u.deleted = true")
    long countDeleted();

    /**
     * 정상 사용자 수 — blocked && deleted 동시 true 도 active 에서 제외. 복합 인덱스
     * users(is_blocked, is_deleted) 사용 (V5 마이그레이션).
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.blocked = false AND u.deleted = false")
    long countActive();

    /** Admin broadcast — 활성 사용자 id 만 청크 페이징. id ASC. */
    @Query("SELECT u.id FROM User u WHERE u.blocked = false AND u.deleted = false AND u.id > :afterId ORDER BY u.id ASC")
    java.util.List<Long> findActiveIdsAfter(@Param("afterId") long afterId, org.springframework.data.domain.Pageable pageable);

    /** Admin 거래 검색 (round 10) — email/nickname LIKE 매칭 user id. limit 으로 IN 절 폭주 방지. */
    @Query("""
            SELECT u.id FROM User u
             WHERE LOWER(u.email)    LIKE LOWER(CONCAT('%', :kw, '%'))
                OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :kw, '%'))
             ORDER BY u.id ASC
            """)
    java.util.List<Long> findIdsByKeywordLike(@Param("kw") String kw, org.springframework.data.domain.Pageable pageable);
}
