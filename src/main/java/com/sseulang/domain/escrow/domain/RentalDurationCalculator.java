package com.sseulang.domain.escrow.domain;

import com.sseulang.domain.item.domain.RentalUnit;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 대여 기간 → 단위 환산 헬퍼.
 *
 * itemPrice 자동 산정 = item.rentalPrice × units(start, end, rentalUnit).
 * 30일 기준 근사 (월). 캘린더 기반 정확 계산은 후속 (시연 시점엔 충분).
 *
 * 순수 도메인 — Spring/JPA 의존 X.
 */
public final class RentalDurationCalculator {

    private RentalDurationCalculator() { }

    /**
     * 대여 기간을 {@code RentalUnit} 으로 올림 환산.
     * 시작 == 종료 거나 음수 기간이면 0 반환 (호출 측이 검증).
     */
    public static long units(LocalDateTime start, LocalDateTime end, RentalUnit unit) {
        if (start == null || end == null || unit == null) {
            throw new IllegalArgumentException("start/end/unit 모두 필수입니다");
        }
        if (!start.isBefore(end)) {
            return 0L;
        }
        long minutes = Duration.between(start, end).toMinutes();
        long divisor = switch (unit) {
            case 시간 -> 60L;
            case 일 -> 60L * 24;
            case 주 -> 60L * 24 * 7;
            case 월 -> 60L * 24 * 30;  // 30일 근사
        };
        // 올림 — 1분이라도 넘으면 1 단위 카운트. 최소 1 보장.
        long ceil = (minutes + divisor - 1) / divisor;
        return Math.max(ceil, 1L);
    }

    /**
     * 자동 산정 itemPrice = rentalPrice × units.
     */
    public static long expectedItemPrice(long rentalPrice, LocalDateTime start, LocalDateTime end, RentalUnit unit) {
        if (rentalPrice < 0) {
            throw new IllegalArgumentException("rentalPrice 는 음수가 될 수 없습니다");
        }
        return rentalPrice * units(start, end, unit);
    }
}
