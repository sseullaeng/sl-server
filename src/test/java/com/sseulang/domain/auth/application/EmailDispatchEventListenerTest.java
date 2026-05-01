package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.event.EmailDispatchRequestedEvent;
import com.sseulang.domain.auth.domain.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailDispatchEventListenerTest {

    private EmailSender emailSender;
    private EmailDispatchEventListener listener;

    @BeforeEach
    void setUp() {
        emailSender = mock(EmailSender.class);
        listener = new EmailDispatchEventListener(emailSender);
    }

    @Test
    @DisplayName("onEmailDispatch 정상_emailSender.sendVerificationEmail 호출")
    void onEmailDispatch_정상() {
        EmailDispatchRequestedEvent event = new EmailDispatchRequestedEvent(
                "user@example.com", "https://app.sseulang.test/verify?token=abc"
        );

        listener.onEmailDispatch(event);

        verify(emailSender).sendVerificationEmail("user@example.com", "https://app.sseulang.test/verify?token=abc");
    }

    @Test
    @DisplayName("onEmailDispatch 발송 실패_RuntimeException 잡고 정상 종료 (가입 흐름 영향 X)")
    void onEmailDispatch_실패() {
        doThrow(new RuntimeException("smtp down")).when(emailSender)
                .sendVerificationEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        EmailDispatchRequestedEvent event = new EmailDispatchRequestedEvent(
                "user@example.com", "https://x"
        );

        // 예외 던지지 않고 정상 종료해야 함 — listener 가 catch 후 로깅만
        assertThatCode(() -> listener.onEmailDispatch(event)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("EmailDispatchRequestedEvent email 누락_거부")
    void event_validation() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new EmailDispatchRequestedEvent(null, "https://x"))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new EmailDispatchRequestedEvent("u@x.com", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
