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
        
    }
}
