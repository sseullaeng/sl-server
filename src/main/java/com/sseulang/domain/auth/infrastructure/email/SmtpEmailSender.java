package com.sseulang.domain.auth.infrastructure.email;

import com.sseulang.domain.auth.domain.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * prod SMTP {@link EmailSender} 구현. Spring Mail (JavaMailSender) 사용.
 *
 * <p>활성화: {@code app.email.smtp-enabled=true} (prod 는 application-prod.yml 에서 강제 true,
 * local 은 .env 에서 SMTP 정보 채우면 켤 수 있음). false / 미설정 시 {@link LogEmailSender} 가 활성.
 * SMTP 설정은 {@code spring.mail.*} 환경변수 주입.</p>
 *
 * <p>발송 실패 시 {@link RuntimeException} 으로 throw — 호출자(LocalAuthService.signup) 가 회원 가입
 * 실패로 트랜잭션 롤백. 5/6 이후 outbox 패턴 도입 시 비동기 발송으로 변경 가능.</p>
 */
@Component
@ConditionalOnProperty(name = "app.email.smtp-enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final String SUBJECT = "[쓸랭] 이메일 인증을 완료해 주세요";

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @org.springframework.beans.factory.annotation.Value("${app.email.from:noreply@sseulang.test}")
            String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String verificationUrl) {
        sendHtml(toEmail, SUBJECT, buildHtml(verificationUrl));
    }

    @Override
    public void sendInquiryReplyEmail(String toEmail, String subject, String html) {
        sendHtml(toEmail, subject, html);
    }

    private void sendHtml(String toEmail, String subject, String html) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 메시지 빌드 실패: " + e.getMessage(), e);
        }
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 운영 모니터링 대상 — 5/6 이후 outbox 도입 시 비동기 retry 로 강화.
            throw new RuntimeException("이메일 발송 실패: " + e.getMessage(), e);
        }
    }

    private static String buildHtml(String verificationUrl) {
        return """
                <!doctype html>
                <html>
                  <body style="font-family:sans-serif;line-height:1.6;color:#333">
                    <h2>쓸랭 이메일 인증</h2>
                    <p>아래 버튼을 눌러 이메일 인증을 완료해 주세요.</p>
                    <p>
                      <a href="%s" style="display:inline-block;padding:12px 24px;background:#5a6cff;color:#fff;text-decoration:none;border-radius:6px">
                        이메일 인증하기
                      </a>
                    </p>
                    <p style="color:#888;font-size:12px">
                      버튼이 동작하지 않으면 다음 링크를 복사해 브라우저에 붙여 넣으세요:<br>
                      <a href="%s">%s</a>
                    </p>
                  </body>
                </html>
                """.formatted(verificationUrl, verificationUrl, verificationUrl);
    }
}
