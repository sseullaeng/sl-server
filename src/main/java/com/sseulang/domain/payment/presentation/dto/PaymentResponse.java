package com.sseulang.domain.payment.presentation.dto;

import com.sseulang.domain.payment.application.dto.PaymentResult;
import com.sseulang.domain.payment.domain.PaymentMethod;
import com.sseulang.domain.payment.domain.PaymentStatus;
import com.sseulang.domain.payment.domain.PaymentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "결제 — 토스페이먼츠 충전. paidAt 채워지면 잔액 충전 완료. status 가 PENDING 이면 webhook 또는 reconciliation 대기.")
public record PaymentResponse(
        @Schema(example = "78") Long id,
        @Schema(example = "100") Long userId,
        @Schema(description = "거래 결제용일 때만 채워짐 (충전형은 null)", example = "12") Long transactionId,
        PaymentType paymentType,
        PaymentMethod method,
        @Schema(example = "50000") long amount,
        PaymentStatus status,
        @Schema(example = "merchant-9f2c8c5b") String merchantUid,
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
