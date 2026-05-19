package com.sseulang.domain.auth.infrastructure.persistence;

import com.sseulang.domain.auth.domain.EmailVerification;
import com.sseulang.domain.auth.domain.EmailVerificationRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class EmailVerificationRepositoryImpl implements EmailVerificationRepository {

    private final EmailVerificationJpaRepository jpa;

    public EmailVerificationRepositoryImpl(EmailVerificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EmailVerification save(EmailVerification verification) {
        return jpa.save(verification);
    }

    @Override
    public Optional<EmailVerification> findByToken(String token) {
        return jpa.findByToken(token);
    }

    @Override
    public int invalidateUnusedSignupTokens(Long userId, java.time.LocalDateTime now) {
        return jpa.invalidateUnusedSignupTokens(userId, now);
    }

    @Override
    public int markUsedIfValid(String token, java.time.LocalDateTime now) {
        return jpa.markUsedIfValid(token, now);
    }
}
