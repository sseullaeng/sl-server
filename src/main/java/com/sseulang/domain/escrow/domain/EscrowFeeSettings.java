package com.sseulang.domain.escrow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 거래대행 수수료 정책 — singleton row (id=1).
 *
 * <p>운영 변경은 admin endpoint 로. 변경 시 진행 중 application 영향 X — application 등록 시
 * snapshot 컬럼에 lock (결정 #9, #12).</p>
 *
 * <p>11 fields = 프론트 calcFees 와 동일 (CC3). multiplier 는 enum 코드 상수.</p>
 */
@Entity
@Table(name = "escrow_fee_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EscrowFeeSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    @Column(name = "fuel_price_per_l", nullable = false)
    private long fuelPricePerL;

    @Column(name = "base_fuel_price", nullable = false)
    private long baseFuelPrice;

    @Column(name = "base_delivery_fee", nullable = false)
    private long baseDeliveryFee;

    @Column(name = "base_km_rate", nullable = false)
    private long baseKmRate;

    @Column(name = "fuel_efficiency", nullable = false, precision = 5, scale = 2)
    private BigDecimal fuelEfficiency;

    @Column(name = "min_delivery_fee", nullable = false)
    private long minDeliveryFee;

    @Column(name = "truck_base_delivery_fee", nullable = false)
    private long truckBaseDeliveryFee;

    @Column(name = "truck_base_km_rate", nullable = false)
    private long truckBaseKmRate;

    @Column(name = "truck_fuel_efficiency", nullable = false, precision = 5, scale = 2)
    private BigDecimal truckFuelEfficiency;

    @Column(name = "truck_min_delivery_fee", nullable = false)
    private long truckMinDeliveryFee;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    /** 수수료 정책 일괄 변경 — admin endpoint 호출. */
    public void apply(
            BigDecimal commissionRate,
            long fuelPricePerL, long baseFuelPrice,
            long baseDeliveryFee, long baseKmRate, BigDecimal fuelEfficiency, long minDeliveryFee,
            long truckBaseDeliveryFee, long truckBaseKmRate, BigDecimal truckFuelEfficiency, long truckMinDeliveryFee,
            Long updatedBy
    ) {
        this.commissionRate = commissionRate;
        this.fuelPricePerL = fuelPricePerL;
        this.baseFuelPrice = baseFuelPrice;
        this.baseDeliveryFee = baseDeliveryFee;
        this.baseKmRate = baseKmRate;
        this.fuelEfficiency = fuelEfficiency;
        this.minDeliveryFee = minDeliveryFee;
        this.truckBaseDeliveryFee = truckBaseDeliveryFee;
        this.truckBaseKmRate = truckBaseKmRate;
        this.truckFuelEfficiency = truckFuelEfficiency;
        this.truckMinDeliveryFee = truckMinDeliveryFee;
        this.updatedBy = updatedBy;
        // updated_at 은 DB ON UPDATE CURRENT_TIMESTAMP — JPA Dirty checking 으로 트리거
    }
}
