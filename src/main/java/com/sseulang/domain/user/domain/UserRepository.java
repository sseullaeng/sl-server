package com.sseulang.domain.user.domain;

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
}
