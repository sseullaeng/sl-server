package com.sseulang.domain.payment.presentation.dto;

import com.sseulang.domain.payment.application.dto.ChargeConfirmCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "토스 결제 승인 — 프론트가 토스 SDK 로 결제 완료 후 paymentKey/orderId/amount 전달. 백엔드가 토스 confirm API 로 검증.")
public record ChargeConfirmRequest(
        @Schema(description = "토스 paymentKey (결제 식별)", example = "tgen_20240101101010A1B2C3")
        @NotBlank String paymentKey,

        @Schema(description = "백엔드 startCharge 응답의 merchantUid (= orderId)", example = "ORD-20260501-abc123")
        @NotBlank String orderId,

        @Schema(description = "결제 금액 (위변조 검증용)", example = "50000")
        @NotNull @Positive Long amount
) {
    public ChargeConfirmCommand toCommand(Long requesterId) {
        return new ChargeConfirmCommand(requesterId, paymentKey, orderId, amount);
    }
}
