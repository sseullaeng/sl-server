package com.sseulang.domain.payment.domain;

import java.time.LocalDateTime;

public record PaymentConfirmResult(
        String paymentKey,
        String orderId,
        long amount,
        PaymentMethod method,
        LocalDateTime approvedAt,
        String rawResponse
) { }
