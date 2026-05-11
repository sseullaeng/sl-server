package com.sseulang.domain.payment.application.dto;

public record ChargeConfirmCommand(
        Long requesterId,
        String paymentKey,
        String orderId,
        long amount
) { }
