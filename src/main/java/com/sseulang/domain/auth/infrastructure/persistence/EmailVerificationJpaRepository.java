package com.sseulang.domain.auth.infrastructure.persistence;

import com.sseulang.domain.auth.domain.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

interface EmailVerificationJpaRepository extends JpaRepository<EmailVerification, Long> {

    Optional<EmailVerification> findByToken(String token);

    /**
     * 단일 atomic UPDATE — 해당 user 의 미사용 SIGNUP 토큰 일괄 만료. 새 토큰 발급 직전 호출.
     */
    @Modifying
    @Query("""
            UPDATE EmailVerification v
               SET v.usedAt = :now
             WHERE v.userId = :userId
               AND v.purpose = com.sseulang.domain.auth.domain.VerificationPurpose.SIGNUP
               AND v.usedAt IS NULL
            """)
    int invalidateUnusedSignupTokens(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 토큰 atomic 소진 — 동시 두 요청이 같은 token 으로 도달해도 1건만 성공 (follow-up #42).
     * clearAutomatically + flushAutomatically 로 영속성 컨텍스트 동기화.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EmailVerification v
               SET v.usedAt = :now
             WHERE v.token = :token
               AND v.usedAt IS NULL
               AND v.expiresAt > :now
            """)
    int markUsedIfValid(@Param("token") String token, @Param("now") LocalDateTime now);
}
