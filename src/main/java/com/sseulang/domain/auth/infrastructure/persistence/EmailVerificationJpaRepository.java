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

    

    @Modifying
    @Query("""
            UPDATE EmailVerification v
               SET v.usedAt = :now
             WHERE v.userId = :userId
               AND v.purpose = com.sseulang.domain.auth.domain.VerificationPurpose.SIGNUP
               AND v.usedAt IS NULL
            """)
    int invalidateUnusedSignupTokens(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    

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
