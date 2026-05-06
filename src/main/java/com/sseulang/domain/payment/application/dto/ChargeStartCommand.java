package com.sseulang.domain.payment.application.dto;

/**
 * 충전 시작 — escrowApplicationId=null 면 일반 충전, NOT NULL 이면 거래대행 결제.
 */
public record ChargeStartCommand(
        Long userId,
        long amount,
        Long escrowApplicationId
) {
    /** 일반 충전 (Day 7 spec) — 호환 secondary constructor. */
    public ChargeStartCommand(Long userId, long amount) {
        this(userId, amount, null);
    }

    public static ChargeStartCommand charge(Long userId, long amount) {
        return new ChargeStartCommand(userId, amount, null);
    }

    public boolean isEscrow() {
        return escrowApplicationId != null;
    }
}
