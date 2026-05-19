package com.sseulang.domain.auth.infrastructure.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpEmailSenderTest {

    private JavaMailSender mailSender;
    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);

        // 실제 MimeMessage 생성을 위해 JavaMailSenderImpl 사용 (createMimeMessage() 만 빌려옴)
        JavaMailSenderImpl real = new JavaMailSenderImpl();
        real.setJavaMailProperties(new Properties());
        when(mailSender.createMimeMessage()).thenReturn(real.createMimeMessage());

        sender = new SmtpEmailSender(mailSender, "noreply@sseulang.test");
    }

    @Test
    @DisplayName("sendVerificationEmail 정상_subject·to·HTML body 포함")
    void send_정상() throws Exception {
        sender.sendVerificationEmail("user@example.com", "https://app.sseulang.kr/verify?token=abc");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        sent.saveChanges();
        assertThat(sent.getAllRecipients()).hasSize(1);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat(sent.getSubject()).contains("이메일 인증");

        String content = (String) sent.getContent();
        assertThat(content).contains("https://app.sseulang.kr/verify?token=abc");
        assertThat(content).contains("이메일 인증하기");
        // MimeMessageHelper.setText(html, true) 가 dataHandler 의 ContentType 을 text/html 로 세팅.
        assertThat(sent.getDataHandler().getContentType()).contains("text/html");
        assertThat(sent.getDataHandler().getContentType().toLowerCase()).contains("utf-8");
    }

    @Test
    @DisplayName("sendVerificationEmail 발송 실패_RuntimeException 으로 wrap")
    void send_실패_wrap() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.sendVerificationEmail("user@example.com", "https://x"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("이메일 발송 실패");
    }
}
