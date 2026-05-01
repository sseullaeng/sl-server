package com.sseulang.domain.payment.presentation.dto;

import com.sseulang.domain.payment.application.dto.ChargeStartResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "충전 시작 응답 — 받은 merchantUid + amount + tossClientKey 로 토스 SDK 결제창 호출.")
public record ChargeStartResponse(
        @Schema(example = "78") Long paymentId,
        @Schema(example = "merchant-9f2c8c5b", description = "토스 결제창에 전달할 주문번호 (UNIQUE, 백엔드 발급)") String merchantUid,
        @Schema(example = "50000") long amount,
        @Schema(example = "test_ck_...", description = "프론트가 토스 SDK 초기화에 사용") String tossClientKey
) {
    public static ChargeStartResponse from(ChargeStartResult r) {
        return new ChargeStartResponse(r.paymentId(), r.merchantUid(), r.amount(), r.tossClientKey());
    }
}
