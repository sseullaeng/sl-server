package com.sseulang.domain.auth.application.event;

/**
 * 이메일 인증 메일 발송 요청 이벤트. {@link com.sseulang.domain.auth.application.EmailVerificationService}
 * 가 토큰 INSERT 후 발행. 별도 listener 가 {@code @TransactionalEventListener(AFTER_COMMIT)} 로
 * 받아 메일 발송 — 트랜잭션 commit 후 발송이라 메일 시스템 다운 시에도 user/token 은 보존,
 * 가입 자체는 차단되지 않음 (follow-up #41).
 *
 * <p>발송 실패는 로그만 — 사용자가 {@code /api/v1/auth/resend-verification} 으로 재시도 가능.</p>
 */
public record EmailDispatchRequestedEvent(
        String email,
        String verificationUrl
) {
    public EmailDispatchRequestedEvent {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email 은 필수입니다");
        }
        if (verificationUrl == null || verificationUrl.isBlank()) {
            throw new IllegalArgumentException("verificationUrl 은 필수입니다");
        }
    }
}
