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
}
