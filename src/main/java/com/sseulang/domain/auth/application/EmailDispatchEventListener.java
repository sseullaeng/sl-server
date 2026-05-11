package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.application.event.EmailDispatchRequestedEvent;
import com.sseulang.domain.auth.domain.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
            
            log.error("[email-dispatch] 발송 실패 email={} reason={}", event.email(), e.getMessage(), e);
        }
    }
}
