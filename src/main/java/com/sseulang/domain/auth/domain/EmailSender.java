package com.sseulang.domain.auth.domain;

public interface EmailSender {

    
    void sendVerificationEmail(String toEmail, String verificationUrl);

    

    void sendInquiryReplyEmail(String toEmail, String subject, String html);

    

    void sendAutoWithdrawnEmail(String toEmail, int cumulativeSuspendDays);
}
