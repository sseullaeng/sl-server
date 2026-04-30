package com.sseulang.domain.user.infrastructure.persistence;

import com.sseulang.domain.user.domain.SocialProvider;
import com.sseulang.domain.user.domain.User;
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
}
