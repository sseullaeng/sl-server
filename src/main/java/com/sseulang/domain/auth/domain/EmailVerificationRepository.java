package com.sseulang.domain.auth.domain;

import java.util.Optional;

public interface EmailVerificationRepository {

    EmailVerification save(EmailVerification verification);

    Optional<EmailVerification> findByToken(String token);

    

    int invalidateUnusedSignupTokens(Long userId, java.time.LocalDateTime now);

    

    int markUsedIfValid(String token, java.time.LocalDateTime now);
}
