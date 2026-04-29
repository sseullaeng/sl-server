package com.sseulang.domain.payment.application.dto;

/**
 * 클라가 토스 redirect 후 백엔드에 확정 요청 시 — paymentKey/orderId/amount 는 토스 응답.
 * requesterId 는 인증된 사용자 (Payment 소유자 검증용).
 */
public record ChargeConfirmCommand(
        Long requesterId,
        String paymentKey,
        String orderId,
        long amount
) { }
