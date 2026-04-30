package com.sseulang.domain.payment.presentation.dto;

import com.sseulang.domain.payment.application.dto.ChargeStartCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "포인트 충전 시작. 토스 결제 준비 단계 — merchantUid + clientKey 발급.")
public record ChargeStartRequest(
        @Schema(description = "충전 금액 (원)", example = "50000")
        @NotNull @Positive Long amount
) {
    public ChargeStartCommand toCommand(Long userId) {
        return new ChargeStartCommand(userId, amount);
    }
}
