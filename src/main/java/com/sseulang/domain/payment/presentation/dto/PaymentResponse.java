package com.sseulang.domain.payment.presentation.dto;

import com.sseulang.domain.payment.application.dto.PaymentResult;
import com.sseulang.domain.payment.domain.PaymentMethod;
import com.sseulang.domain.payment.domain.PaymentStatus;
import com.sseulang.domain.payment.domain.PaymentType;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long userId,
        Long transactionId,
        PaymentType paymentType,
        PaymentMethod method,
        long amount,
        PaymentStatus status,
        String merchantUid,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(PaymentResult r) {
        return new PaymentResponse(
                r.id(), r.userId(), r.transactionId(),
                r.paymentType(), r.method(),
                r.amount(), r.status(),
                r.merchantUid(),
                r.paidAt(), r.createdAt()
        );
    }
}
