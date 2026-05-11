package com.sseulang.domain.user.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * User Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 구현은 {@code domain/user/infrastructure/persistence}.
 */
public interface UserRepository {

    Optional<User> findById(Long id);

    Optional<User> findBySocial(SocialProvider provider, String socialId);

    Optional<User> findByEmail(Email email);

    User save(User user);

    /** 관리자 회원 목록 페이징. 차단/삭제 상태 필터는 후속, 일단 전체 노출. created_at DESC. */
    Page<User> findAllForAdmin(Pageable pageable);

    /**
     * Admin 회원 검색 — keyword(nickname/email LIKE), status filter, created_at 범위.
     * status 는 derive 기반 — DB 컬럼 직접 매칭 대신 SQL 조건으로 변환.
     * dormantThresholdDays / now 는 DORMANT/SUSPENDED 계산 기준.
     */
    Page<User> searchForAdmin(
            com.sseulang.domain.user.application.dto.AdminUserSearchCriteria criteria,
            java.time.LocalDateTime now,
            int dormantThresholdDays,
            Pageable pageable
    );

    /**
     * 가이드 §4.7 — 리뷰 작성 시점에 review_count / rating_sum 을 단일 원자 UPDATE 로 누적하고
     * trust_score 를 즉시 재계산. Codex 게이트 2 (2026-04-29) 보강 — REPEATABLE_READ + AVG 서브쿼리
     * 의 stale read view 회귀를 누적 컬럼으로 차단.
     */
    int recordReviewFor(Long revieweeId, int rating);

    /**
     * 가이드 §4.8 — 포인트 잔액 atomic 증가 (충전 / 거래 정산 적립). amount 양수 강제.
     * 단일 SQL UPDATE 라 동시 충전 race 안전. 영향받은 행 수 반환.
     */
    int creditPointBalance(Long userId, long amount);

    /**
     * 가이드 §4.8 / §5.3 — 포인트 잔액 atomic 차감 (거래 결제 / 출금 신청). amount 양수 강제.
     * 잔액 부족 시 affected=0 (음수 방지 가드 — WHERE point_balance >= :amount).
     * 단일 SQL UPDATE + 행 락 직렬화로 동시 차감 race 안전.
     */
    int deductPointBalance(Long userId, long amount);

    /**
     * 잔액 단건 scalar 조회. atomic UPDATE 직후 balance_after 적재용 — 영속성 컨텍스트 stale 우회.
     */
    Long findPointBalance(Long userId);

    /**
     * 가이드 §5.1 거래 hold (라운드 11) — point_balance 에서 amount 차감 + point_hold 에 적립.
     * 단일 원자 UPDATE 로 두 컬럼 동시 변경 → race-safe + buyer 잔액 부족 가드 (WHERE point_balance >= :amount).
     * 영향 행 0 = 잔액 부족, 1 = 정상.
     */
    int holdForEscrow(Long userId, long amount);

    /**
     * 거래완료 정산 — buyer point_hold 만 차감 (seller credit 은 creditPointBalance 별도 호출).
     * 단일 원자 UPDATE. point_hold &lt; amount 이면 affected=0 (가드 회귀 시 호출자 fail-fast).
     */
    int releaseHold(Long userId, long amount);

    /**
     * 거래 취소 환불 — point_hold 에서 차감 + point_balance 로 적립.
     * 단일 원자 UPDATE 로 두 컬럼 동시 변경. point_hold &lt; amount 이면 affected=0.
     */
    int refundHold(Long userId, long amount);

    /**
     * point_hold scalar 조회. atomic UPDATE 직후 history balance_after 적재용 (영속성 컨텍스트 stale 우회).
     */
    Long findPointHold(Long userId);

    /**
     * (point_balance, point_hold) 단일 SELECT 스냅샷. 잔액 표시 응답 (3분할) 의 race-safe 일관성 보장 —
     * 두 컬럼을 분리 read 하면 동시 reserve/cancel/refund 사이에 끼어 합산이 어긋날 수 있음 (게이트 1 W-1).
     */
    java.util.Optional<PointSnapshot> findPointSnapshot(Long userId);

    record PointSnapshot(long balance, long hold) { }

    // ───────── 관리자 통계 ─────────

    /** 전체 사용자 수 (차단/삭제 포함). */
    long countAll();

    /** is_blocked=true 사용자 수. */
    long countBlocked();

    /** is_deleted=true 사용자 수. */
    long countDeleted();

    /**
     * 정상(차단 X, 삭제 X) 사용자 수. blocked && deleted 동시 true 인 사용자가 있어도 정확.
     * 게이트 2 보강 — total - blocked - deleted 의 이중 차감 회피.
     */
    long countActive();

    /**
     * 차트 dashboard — 기간 안 (created_at &gt;= from AND created_at &lt; to) 가입자 수.
     * to 는 exclusive. summary.users.monthDelta / todaySignups.* 등 기간 합산용.
     */
    long countSignupsBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);

    /**
     * 차트 dashboard — 일자별 가입자 수. 빈 일은 결과에 미포함 (호출자가 0으로 채움).
     * created_at DATE GROUP BY, ASC.
     */
    java.util.List<DailyCount> findDailySignups(java.time.LocalDateTime from, java.time.LocalDateTime to);

    /** {@code (date, count)} record — 일자별 차트 데이터용. */
    record DailyCount(java.time.LocalDate date, long count) { }

    /**
     * Admin broadcast 용 — 활성 사용자 id 청크 페이징. blocked/deleted 제외.
     * 큰 사용자 수에서 OOM 방지를 위해 호출자가 청크 단위로 호출. id ASC 안정 정렬.
     *
     * @param afterId 이전 호출의 마지막 id (처음 호출은 0)
     * @param limit   페이지 크기
     */
    java.util.List<Long> findActiveIdsAfter(long afterId, int limit);

    /**
     * Admin 거래 검색 (round 10) cross-aggregate keyword 매칭용 — email/nickname LIKE.
     * 결과 id 만 반환 → 호출자가 IN 절로 사용. limit 으로 IN 절 폭주 방지.
     * keyword null/blank → 빈 리스트.
     */
    java.util.List<Long> findIdsByKeywordLike(String keyword, int limit);

    /**
     * 라운드 12 PR-F #8 — 누적 정지 일수가 {@code threshold} 이상이면서 아직 soft delete 되지 않은
     * 사용자 id 청크. id ASC. 배치 자동 탈퇴 처리용.
     */
    java.util.List<Long> findAutoWithdrawTargetIds(int threshold, int limit);
}
