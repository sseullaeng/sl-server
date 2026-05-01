package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.event.EmailDispatchRequestedEvent;
import com.sseulang.domain.auth.domain.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 이메일 인증 메일 비동기 발송 listener. 트랜잭션 commit 후 발송 (follow-up #41 atomicity 보장):
 * <ul>
 *   <li>user / token 은 commit 된 후라 메일 발송 실패해도 dangling 없음</li>
 *   <li>발송 실패는 로깅만 — 사용자가 {@code /resend-verification} 으로 재시도</li>
 *   <li>{@code AFTER_COMMIT} phase — 트랜잭션 롤백 시 listener 호출 X (가입 실패 → 메일 발송 X)</li>
 * </ul>
 *
 * <p>현재는 동기 처리 (호출 스레드). 응답 지연 issue 발생 시 {@code @Async} 추가 또는 outbox
 * 패턴으로 교체.</p>
 */
@Component
public class EmailDispatchEventListener {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchEventListener.class);

    private final EmailSender emailSender;

    public EmailDispatchEventListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailDispatch(EmailDispatchRequestedEvent event) {
        try {
            emailSender.sendVerificationEmail(event.email(), event.verificationUrl());
        } catch (RuntimeException e) {
            // 가입은 이미 commit 됨 — 메일 발송 실패만 로깅. 사용자가 /resend-verification 으로 재시도.
            log.error("[email-dispatch] 발송 실패 email={} reason={}", event.email(), e.getMessage(), e);
        }
    }
}
