package com.sseulang.domain.auth.infrastructure.email;

import com.sseulang.domain.auth.domain.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 개발/테스트용 EmailSender — 실제 SMTP 호출 없이 로그에 인증 URL 만 출력.
 * prod 환경에서는 별도 SMTP 어댑터(SES/SendGrid) 가 등록되어야 한다 (5/6 이후 후속).
 *
 * <p>{@code @Profile({"local","dev","test"})} — 명시 allowlist. {@code @Profile("!prod")} 는
 * prod 프로필 누락(빈 active profile) 시 빈이 활성화돼 토큰 URL 이 운영 로그에 새는 회귀 위험.
 * prod 는 SMTP 어댑터 미등록 시 부팅 실패하도록 둔다 (게이트 1 round 2).</p>
 *
 * <p>또한 {@code app.email.smtp-enabled=true} 면 비활성 — local 에서도 SMTP 직접 테스트 가능하도록.
 * matchIfMissing=true 라 미설정 (local default) 은 LogEmailSender 활성화.</p>
 */
@Component
@Profile({"local", "dev", "test"})
@ConditionalOnProperty(name = "app.email.smtp-enabled", havingValue = "false", matchIfMissing = true)
public class LogEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void sendVerificationEmail(String toEmail, String verificationUrl) {
        log.info("[email-verification] to={} url={}", toEmail, verificationUrl);
    }

    @Override
    public void sendInquiryReplyEmail(String toEmail, String subject, String html) {
        log.info("[inquiry-reply] to={} subject={} (html len={})", toEmail, subject, html == null ? 0 : html.length());
    }

    @Override
    public void sendAutoWithdrawnEmail(String toEmail, int cumulativeSuspendDays) {
        log.info("[auto-withdrawn] to={} cumulativeSuspendDays={}", toEmail, cumulativeSuspendDays);
    }
}
