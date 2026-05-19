package com.sseulang.domain.overdue.domain;

public record OverdueThresholds(
        int phase3AmountKrw,
        int phase3DaysThreshold,
        int phase4DaysAfterSuspend,
        int phase2DailyRatePercent
) {
    public static OverdueThresholds defaults() {
        return new OverdueThresholds(50_000, 14, 7, 20);
    }

    public OverdueThresholds {
        if (phase3AmountKrw <= 0) {
            throw new IllegalArgumentException("phase3AmountKrw 는 양수여야 합니다");
        }
        if (phase3DaysThreshold <= 0) {
            throw new IllegalArgumentException("phase3DaysThreshold 는 양수여야 합니다");
        }
        if (phase4DaysAfterSuspend <= 0) {
            throw new IllegalArgumentException("phase4DaysAfterSuspend 는 양수여야 합니다");
        }
        if (phase2DailyRatePercent < 0) {
            throw new IllegalArgumentException("phase2DailyRatePercent 는 음수가 될 수 없습니다");
        }
    }
}
