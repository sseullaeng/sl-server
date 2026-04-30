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
}
