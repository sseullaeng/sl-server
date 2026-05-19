package com.sseulang.domain.payment.application.dto;

public record ChargeStartResult(
        Long paymentId,
        String merchantUid,
        long amount,
        String tossClientKey
) { }
