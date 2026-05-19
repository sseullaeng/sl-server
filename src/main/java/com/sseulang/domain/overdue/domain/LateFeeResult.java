package com.sseulang.domain.overdue.domain;

public record LateFeeResult(long forfeited, long remainingDeposit, long extraDebt) {
    public LateFeeResult {
        if (forfeited < 0 || remainingDeposit < 0 || extraDebt < 0) {
            throw new IllegalArgumentException("연체 산정 금액은 음수가 될 수 없습니다");
        }
    }

    public static LateFeeResult zero(long deposit) {
        return new LateFeeResult(0, deposit, 0);
    }
}
