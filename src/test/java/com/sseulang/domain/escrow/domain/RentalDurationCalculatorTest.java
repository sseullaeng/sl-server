package com.sseulang.domain.escrow.domain;

import com.sseulang.domain.item.domain.RentalUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentalDurationCalculatorTest {

    @Test
    @DisplayName("units_정확히_n일_n_반환")
    void units_n_days_exact() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        assertThat(RentalDurationCalculator.units(start, start.plusDays(3), RentalUnit.일)).isEqualTo(3L);
    }

    @Test
    @DisplayName("units_2일_2시간_올림_3일")
    void units_ceil_to_next_day() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime end = start.plusDays(2).plusHours(2);
        assertThat(RentalDurationCalculator.units(start, end, RentalUnit.일)).isEqualTo(3L);
    }

    @Test
    @DisplayName("units_시간_단위_45분도_1시간으로_올림")
    void units_hour_round_up() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        assertThat(RentalDurationCalculator.units(start, start.plusMinutes(45), RentalUnit.시간)).isEqualTo(1L);
        assertThat(RentalDurationCalculator.units(start, start.plusMinutes(60), RentalUnit.시간)).isEqualTo(1L);
        assertThat(RentalDurationCalculator.units(start, start.plusMinutes(61), RentalUnit.시간)).isEqualTo(2L);
    }

    @Test
    @DisplayName("units_주_단위_8일_2주")
    void units_week() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        assertThat(RentalDurationCalculator.units(start, start.plusDays(7), RentalUnit.주)).isEqualTo(1L);
        assertThat(RentalDurationCalculator.units(start, start.plusDays(8), RentalUnit.주)).isEqualTo(2L);
    }

    @Test
    @DisplayName("units_월_단위_30일_근사_1개월")
    void units_month_30day_approx() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        assertThat(RentalDurationCalculator.units(start, start.plusDays(30), RentalUnit.월)).isEqualTo(1L);
        assertThat(RentalDurationCalculator.units(start, start.plusDays(31), RentalUnit.월)).isEqualTo(2L);
    }

    @Test
    @DisplayName("units_start>=end_0_반환")
    void units_zero_when_invalid_range() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 10, 0);
        assertThat(RentalDurationCalculator.units(now, now, RentalUnit.일)).isZero();
        assertThat(RentalDurationCalculator.units(now, now.minusHours(1), RentalUnit.일)).isZero();
    }

    @Test
    @DisplayName("units_null_입력_IllegalArgument")
    void units_null_inputs() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 1, 10, 0);
        assertThatThrownBy(() -> RentalDurationCalculator.units(null, now, RentalUnit.일))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RentalDurationCalculator.units(now, null, RentalUnit.일))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RentalDurationCalculator.units(now, now.plusDays(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("expectedItemPrice_단가_3000_3일_9000")
    void expectedItemPrice_normal() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        long price = RentalDurationCalculator.expectedItemPrice(3_000L, start, start.plusDays(3), RentalUnit.일);
        assertThat(price).isEqualTo(9_000L);
    }

    @Test
    @DisplayName("expectedItemPrice_음수_단가_거부")
    void expectedItemPrice_negative_price() {
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 10, 0);
        assertThatThrownBy(() ->
                RentalDurationCalculator.expectedItemPrice(-1L, start, start.plusDays(3), RentalUnit.일))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
