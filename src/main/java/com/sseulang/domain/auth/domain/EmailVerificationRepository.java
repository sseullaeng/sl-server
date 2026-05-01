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

    /**
     * 토큰 단일 atomic 소진 (follow-up #42 race 차단).
     *
     * <p>{@code UPDATE email_verifications SET used_at=? WHERE token=? AND used_at IS NULL
     * AND expires_at > ?} — 동시 두 요청이 같은 token 으로 도달해도 정확히 1건만 1 rows affected.
     * 0 rows = 미존재 / 이미 사용 / 만료 — 호출자가 사유 분기 (선택).</p>
     *
     * @return 영향 행 수 (0 또는 1)
     */
    int markUsedIfValid(String token, java.time.LocalDateTime now);
}
