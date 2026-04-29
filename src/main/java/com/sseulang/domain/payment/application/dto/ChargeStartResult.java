package com.sseulang.domain.payment.application.dto;

/**
 * 충전 시작 응답 — 클라이언트가 토스 위젯에 사용. 토스 결제 완료 후 redirect 시
 * {@code orderId == merchantUid}, {@code amount} 그대로 confirm endpoint 에 전달.
 */
public record ChargeStartResult(
        Long paymentId,
        String merchantUid,
        long amount,
        String tossClientKey
) { }
