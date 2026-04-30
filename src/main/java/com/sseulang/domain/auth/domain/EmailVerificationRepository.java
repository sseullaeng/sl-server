package com.sseulang.domain.auth.domain;

import java.util.Optional;

public interface EmailVerificationRepository {

    EmailVerification save(EmailVerification verification);

    Optional<EmailVerification> findByToken(String token);

    /**
     * 해당 user 의 미사용 SIGNUP 토큰을 모두 used 처리. 새 토큰 발급 직전 호출 — 메일 폭탄/구토큰 누적 차단.
     * 영향받은 행 수 반환.
     */
    int invalidateUnusedSignupTokens(Long userId, java.time.LocalDateTime now);
}
