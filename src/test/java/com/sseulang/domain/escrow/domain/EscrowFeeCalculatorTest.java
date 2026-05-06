package com.sseulang.domain.escrow.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * EscrowFeeCalculator 단위 테스트 — pure function. 게이트 1 보안 영역 (결제 산정 정확성).
 */
class EscrowFeeCalculatorTest {

    private static EscrowFeeSettings defaultSettings() {
        EscrowFeeSettings s = new EscrowFeeSettings();
        ReflectionTestUtils.setField(s, "id", 1L);
        ReflectionTestUtils.setField(s, "commissionRate", new BigDecimal("0.0500"));
        ReflectionTestUtils.setField(s, "fuelPricePerL", 1650L);
        ReflectionTestUtils.setField(s, "baseFuelPrice", 1650L);  // diff=0 → 보정 0
        ReflectionTestUtils.setField(s, "baseDeliveryFee", 1500L);
        ReflectionTestUtils.setField(s, "baseKmRate", 500L);
        ReflectionTestUtils.setField(s, "fuelEfficiency", new BigDecimal("25.00"));
        ReflectionTestUtils.setField(s, "minDeliveryFee", 3000L);
        ReflectionTestUtils.setField(s, "truckBaseDeliveryFee", 5000L);
        ReflectionTestUtils.setField(s, "truckBaseKmRate", 1200L);
        ReflectionTestUtils.setField(s, "truckFuelEfficiency", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(s, "truckMinDeliveryFee", 15000L);
        ReflectionTestUtils.setField(s, "updatedAt", LocalDateTime.now());
        return s;
    }

    @Test
    @DisplayName("distanceKm_같은좌표_0km")
    void distanceKm_same_coordinate() {
        BigDecimal d = EscrowFeeCalculator.distanceKm(37.5, 127.0, 37.5, 127.0);
        assertThat(d).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("distanceKm_강남강서_약16km")
    void distanceKm_haversine_real() {
        // 강남역 (37.4979, 127.0276) ↔ 강서구청 (37.5510, 126.8495)
        BigDecimal d = EscrowFeeCalculator.distanceKm(37.4979, 127.0276, 37.5510, 126.8495);
        assertThat(d.doubleValue()).isBetween(15.0, 17.0);
    }

    @Test
    @DisplayName("calculate_INTERNAL_buyer결제_itemPrice포함")
    void calculate_internal_total_includes_itemPrice() {
        EscrowFeeSettings s = defaultSettings();
        FeeBreakdown fb = EscrowFeeCalculator.calculate(
                s, TradeMode.INTERNAL, 1_000_000L,
                new BigDecimal("8.50"),
                Weight.R1TO3, Volume.M, Fragility.F3
        );
        // commission = 1M × 0.05 = 50,000
        assertThat(fb.commissionFee()).isEqualTo(50_000L);
        // total = item + delivery + commission
        assertThat(fb.totalFee()).isEqualTo(1_000_000L + fb.deliveryFee() + 50_000L);
        assertThat(fb.deliveryFee()).isPositive();
        assertThat(fb.commissionRate()).isEqualByComparingTo("0.0500");
    }

    @Test
    @DisplayName("calculate_EXTERNAL_itemPrice0_commission0")
    void calculate_external_no_item_no_commission() {
        EscrowFeeSettings s = defaultSettings();
        FeeBreakdown fb = EscrowFeeCalculator.calculate(
                s, TradeMode.EXTERNAL, 0L,
                new BigDecimal("5.00"),
                Weight.LT1, Volume.S, Fragility.F1
        );
        assertThat(fb.commissionFee()).isZero();
        assertThat(fb.totalFee()).isEqualTo(fb.deliveryFee());  // delivery 만
    }

    @Test
    @DisplayName("calculate_5kg이상_용달차_분기")
    void calculate_truck_branch() {
        EscrowFeeSettings s = defaultSettings();
        // 동일 거리 — 일반 vs 용달
        BigDecimal dist = new BigDecimal("10.00");
        FeeBreakdown normal = EscrowFeeCalculator.calculate(s, TradeMode.EXTERNAL, 0L, dist, Weight.R3TO5, Volume.M, Fragility.F1);
        FeeBreakdown truck = EscrowFeeCalculator.calculate(s, TradeMode.EXTERNAL, 0L, dist, Weight.R5TO10, Volume.M, Fragility.F1);
        // 용달이 일반보다 비쌈 (truckBaseDeliveryFee 5000 + km_rate 1200 vs 1500 + 500)
        assertThat(truck.deliveryFee()).isGreaterThan(normal.deliveryFee());
    }

    @Test
    @DisplayName("calculate_거리짧음_minDeliveryFee_floor")
    void calculate_min_floor() {
        EscrowFeeSettings s = defaultSettings();
        // 0.1km — 1500 + 500*0.1 = 1550, weight×volume×frag multiplier 1.0 → 1550
        // minDeliveryFee 3000 → floor 적용 후 3000 × 1.0 = 3000
        FeeBreakdown fb = EscrowFeeCalculator.calculate(
                s, TradeMode.EXTERNAL, 0L,
                new BigDecimal("0.10"),
                Weight.LT1, Volume.S, Fragility.F1
        );
        assertThat(fb.deliveryFee()).isEqualTo(3000L);  // minDeliveryFee 적용
    }

    @Test
    @DisplayName("calculate_INTERNAL_itemPrice0_FORM_INVALID")
    void calculate_internal_zero_item_rejected() {
        EscrowFeeSettings s = defaultSettings();
        assertThatThrownBy(() -> EscrowFeeCalculator.calculate(
                s, TradeMode.INTERNAL, 0L,
                new BigDecimal("5.00"),
                Weight.LT1, Volume.S, Fragility.F1
        )).isInstanceOf(BusinessException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.ESCROW_FORM_INVALID);
    }

    @Test
    @DisplayName("calculate_EXTERNAL_itemPriceNot0_FORM_INVALID")
    void calculate_external_with_item_rejected() {
        EscrowFeeSettings s = defaultSettings();
        assertThatThrownBy(() -> EscrowFeeCalculator.calculate(
                s, TradeMode.EXTERNAL, 100_000L,
                new BigDecimal("5.00"),
                Weight.LT1, Volume.S, Fragility.F1
        )).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("verifyTolerance_정확일치_통과")
    void verifyTolerance_exact_pass() {
        FeeBreakdown calculated = new FeeBreakdown(
                new BigDecimal("8.50"), 12000L, 90000L, 1_902_000L, new BigDecimal("0.0500")
        );
        assertThatNoException().isThrownBy(() ->
                EscrowFeeCalculator.verifyTolerance(calculated, 12000L, 90000L, 1_902_000L)
        );
    }

    @Test
    @DisplayName("verifyTolerance_5원차이_통과(±10원)")
    void verifyTolerance_within_5won_pass() {
        FeeBreakdown calculated = new FeeBreakdown(
                new BigDecimal("8.50"), 12000L, 90000L, 1_902_000L, new BigDecimal("0.0500")
        );
        assertThatNoException().isThrownBy(() ->
                EscrowFeeCalculator.verifyTolerance(calculated, 12005L, 89998L, 1_902_007L)
        );
    }

    @Test
    @DisplayName("verifyTolerance_11원차이_FEE_MISMATCH")
    void verifyTolerance_11won_diff_rejected() {
        FeeBreakdown calculated = new FeeBreakdown(
                new BigDecimal("8.50"), 12000L, 90000L, 1_902_000L, new BigDecimal("0.0500")
        );
        assertThatThrownBy(() ->
                EscrowFeeCalculator.verifyTolerance(calculated, 12011L, 90000L, 1_902_000L)
        ).isInstanceOf(BusinessException.class)
         .extracting(e -> ((BusinessException) e).getErrorCode())
         .isEqualTo(ErrorCode.ESCROW_FEE_MISMATCH);
    }
}
