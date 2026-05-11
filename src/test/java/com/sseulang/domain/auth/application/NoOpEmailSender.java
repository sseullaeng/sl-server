package com.sseulang.domain.auth.application;

import com.sseulang.domain.auth.domain.EmailSender;

/**
 * 단위 테스트용 no-op {@link EmailSender}. 메일 발송이 검증 대상이 아닌 service 단위 테스트가 사용.
 * 메일 발송 검증이 필요한 곳은 Mockito mock 또는 콜백 캡처 fake 를 별도 사용.
 */
public class NoOpEmailSender implements EmailSender {
    @Override public void sendVerificationEmail(String toEmail, String verificationUrl) { }
    @Override public void sendInquiryReplyEmail(String toEmail, String subject, String html) { }
    @Override public void sendAutoWithdrawnEmail(String toEmail, int cumulativeSuspendDays) { }
}
