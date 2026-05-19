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

@Component
@ConditionalOnProperty(name = "app.email.smtp-enabled", havingValue = "true")
public class SmtpEmailSender implements EmailSender {

    private static final String SUBJECT = "[쓸랭] 이메일 인증을 완료해 주세요";
    private static final String AUTO_WITHDRAWN_SUBJECT = "[쓸랭] 활동 정지 누적 200일 도달 — 계정 자동 탈퇴 안내";

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

    @Override
    public void sendAutoWithdrawnEmail(String toEmail, int cumulativeSuspendDays) {
        sendHtml(toEmail, AUTO_WITHDRAWN_SUBJECT, buildAutoWithdrawnHtml(cumulativeSuspendDays));
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
            
            throw new RuntimeException("이메일 발송 실패: " + e.getMessage(), e);
        }
    }

    private static String buildAutoWithdrawnHtml(int cumulativeSuspendDays) {
        return """
                <!doctype html>
                <html>
                  <body style="font-family:sans-serif;line-height:1.6;color:#333">
                    <h2>쓸랭 계정 자동 탈퇴 안내</h2>
                    <p>회원님의 계정이 활동 정지 누적 <b>%d일</b> 도달로 자동 탈퇴 처리되었습니다.</p>
                    <p>운영 정책상 누적 정지 200일 이상은 자동 탈퇴 대상입니다 (이용약관 제○조).</p>
                    <p>문의는 고객센터(support@sseulang.store) 로 보내 주세요.</p>
                  </body>
                </html>
                """.formatted(cumulativeSuspendDays);
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
