package com.sseulang.domain.auth.domain;

/**
 * 이메일 발송 추상화. 도메인 layer 인터페이스 — SMTP / SES / SendGrid 등 구현은 infrastructure.
 *
 * <p>본 PR scope: {@code LogEmailSender} (개발용 — 콘솔에 인증 URL 출력) 만 구현. prod SMTP
 * 어댑터는 5/6 이후 후속.</p>
 */
public interface EmailSender {

    /** 이메일 인증 토큰 발송. URL 은 호출자(EmailVerificationService) 가 token 으로 조립해 전달. */
    void sendVerificationEmail(String toEmail, String verificationUrl);

    /**
     * 1:1 문의 답변 완료 알림 (round 8c #9). subject/text 모두 호출자 책임 — 본 어댑터는 발송만.
     * SMTP 미설치 환경에선 LogEmailSender 가 콘솔 출력만 하고 안전 fallback.
     */
    void sendInquiryReplyEmail(String toEmail, String subject, String html);

    /**
     * 활동 정지 누적 200일 도달로 자동 탈퇴 처리되었음을 안내 (round 12 PR-F #8).
     * 발송 실패는 호출자에서 swallow — 자동 탈퇴 자체는 이미 commit 된 상태.
     */
    void sendAutoWithdrawnEmail(String toEmail, int cumulativeSuspendDays);
}
