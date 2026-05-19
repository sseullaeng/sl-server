package com.sseulang.domain.auth.infrastructure.email;

import com.sseulang.domain.auth.domain.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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
