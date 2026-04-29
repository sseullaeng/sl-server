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
}
