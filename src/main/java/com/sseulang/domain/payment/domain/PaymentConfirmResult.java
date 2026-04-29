package com.sseulang.domain.payment.domain;

import java.time.LocalDateTime;

/**
 * PG (토스 등) 의 confirm API 응답 — 도메인이 의존하는 형태. 외부 PG 응답 형식 변경에 대한 anti-corruption.
 */
public record PaymentConfirmResult(
        String paymentKey,
        String orderId,
        long amount,
        PaymentMethod method,
        LocalDateTime approvedAt,
        String rawResponse
) { }
