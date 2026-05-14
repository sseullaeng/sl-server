package com.sseulang.domain.overdue.domain;

import java.util.List;

public final class LateFeeCalculator {

    private static final List<DailyForfeit> PHASE1_TABLE = List.of(
            new DailyForfeit(1, 30),
            new DailyForfeit(2, 10),
            new DailyForfeit(3, 10),
            new DailyForfeit(4, 10),
            new DailyForfeit(5, 10),
            new DailyForfeit(6, 10),
            new DailyForfeit(7, 10)
    );

    private LateFeeCalculator() {
    }

    public static LateFeeResult calculate(long deposit, int overdueDays, int phase2RatePercent) {
        if (deposit < 0) {
            throw new IllegalArgumentException("deposit 은 음수가 될 수 없습니다");
        }
        if (phase2RatePercent < 0) {
            throw new IllegalArgumentException("phase2RatePercent 는 음수가 될 수 없습니다");
        }
        if (overdueDays <= 0) {
            return LateFeeResult.zero(deposit);
        }

        int forfeitPercent = 0;
        int phase1Days = Math.min(overdueDays, PHASE1_TABLE.size());
        for (int i = 0; i < phase1Days; i++) {
            forfeitPercent += PHASE1_TABLE.get(i).rate();
        }

        long forfeited = percentageOf(deposit, forfeitPercent);
        long remainingDeposit = deposit - forfeited;

        long extraDebt = 0;
        if (overdueDays > PHASE1_TABLE.size()) {
            int phase2Days = overdueDays - PHASE1_TABLE.size();
            long dailyDebt = percentageOf(deposit, phase2RatePercent);
            extraDebt = Math.multiplyExact(dailyDebt, phase2Days);
        }

        return new LateFeeResult(forfeited, remainingDeposit, extraDebt);
    }

    private static long percentageOf(long amount, int percent) {
        return Math.multiplyExact(amount, percent) / 100;
    }

    private record DailyForfeit(int day, int rate) {
    }
}
