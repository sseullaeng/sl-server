package com.sseulang.domain.payment.application.dto;

import com.sseulang.domain.payment.domain.Payment;
import com.sseulang.domain.payment.domain.PaymentMethod;
import com.sseulang.domain.payment.domain.PaymentStatus;
import com.sseulang.domain.payment.domain.PaymentType;

import java.time.LocalDateTime;

public record PaymentResult(
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
    public static PaymentResult from(Payment p) {
        return new PaymentResult(
                p.getId(), p.getUserId(), p.getTransactionId(),
                p.getPaymentType(), p.getMethod(),
                p.getAmount(), p.getStatus(),
                p.getMerchantUid(),
                p.getPaidAt(),
                p.getCreatedAt()
        );
    }
}
