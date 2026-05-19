package com.sseulang.domain.payment.application.dto;

public record ChargeStartCommand(
        Long userId,
        long amount,
        Long escrowApplicationId
) {
    
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
