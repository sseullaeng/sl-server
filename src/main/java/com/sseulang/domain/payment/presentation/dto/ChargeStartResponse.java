package com.sseulang.domain.payment.presentation.dto;

import com.sseulang.domain.payment.application.dto.ChargeStartResult;

public record ChargeStartResponse(
        Long paymentId,
        String merchantUid,
        long amount,
        String tossClientKey
) {
    public static ChargeStartResponse from(ChargeStartResult r) {
        return new ChargeStartResponse(r.paymentId(), r.merchantUid(), r.amount(), r.tossClientKey());
    }
}
