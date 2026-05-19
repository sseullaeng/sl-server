package com.sseulang.domain.overdue.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LateFeeCalculatorTest {

    @Test
    @DisplayName("calculate_0일차는_보증금_전액_잔여")
    void calculate_zero_days() {
        LateFeeResult result = LateFeeCalculator.calculate(100_000L, 0, 20);

        assertThat(result.forfeited()).isZero();
        assertThat(result.remainingDeposit()).isEqualTo(100_000L);
        assertThat(result.extraDebt()).isZero();
    }

    @Test
    @DisplayName("calculate_1일차는_보증금_30퍼센트_몰수")
    void calculate_day_1() {
        LateFeeResult result = LateFeeCalculator.calculate(100_000L, 1, 20);

        assertThat(result.forfeited()).isEqualTo(30_000L);
        assertThat(result.remainingDeposit()).isEqualTo(70_000L);
        assertThat(result.extraDebt()).isZero();
    }

    @Test
    @DisplayName("calculate_7일차는_보증금_90퍼센트_몰수")
    void calculate_day_7() {
        LateFeeResult result = LateFeeCalculator.calculate(100_000L, 7, 20);

        assertThat(result.forfeited()).isEqualTo(90_000L);
        assertThat(result.remainingDeposit()).isEqualTo(10_000L);
        assertThat(result.extraDebt()).isZero();
    }

    @Test
    @DisplayName("calculate_8일차부터_Phase2_추가채무_1일분_발생")
    void calculate_day_8() {
        LateFeeResult result = LateFeeCalculator.calculate(100_000L, 8, 20);

        assertThat(result.forfeited()).isEqualTo(90_000L);
        assertThat(result.remainingDeposit()).isEqualTo(10_000L);
        assertThat(result.extraDebt()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("calculate_30일차는_Phase2_23일분_추가채무_발생")
    void calculate_day_30() {
        LateFeeResult result = LateFeeCalculator.calculate(100_000L, 30, 20);

        assertThat(result.forfeited()).isEqualTo(90_000L);
        assertThat(result.remainingDeposit()).isEqualTo(10_000L);
        assertThat(result.extraDebt()).isEqualTo(460_000L);
    }

    @Test
    @DisplayName("calculate_큰_금액_overflow는_예외")
    void calculate_overflow_guard() {
        assertThatThrownBy(() -> LateFeeCalculator.calculate(Long.MAX_VALUE, 8, 100))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    @DisplayName("calculate_음수_입력_거부")
    void calculate_negative_input_rejected() {
        assertThatThrownBy(() -> LateFeeCalculator.calculate(-1L, 1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LateFeeCalculator.calculate(100_000L, 1, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
