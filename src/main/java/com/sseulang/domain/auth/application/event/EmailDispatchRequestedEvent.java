package com.sseulang.domain.auth.application.event;

public record EmailDispatchRequestedEvent(
        String email,
        String verificationUrl
) {
    public EmailDispatchRequestedEvent {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email 은 필수입니다");
        }
        if (verificationUrl == null || verificationUrl.isBlank()) {
            throw new IllegalArgumentException("verificationUrl 은 필수입니다");
        }
    }
}
