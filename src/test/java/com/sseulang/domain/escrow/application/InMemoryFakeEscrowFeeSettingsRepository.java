package com.sseulang.domain.escrow.application;

import com.sseulang.domain.escrow.domain.EscrowFeeSettings;
import com.sseulang.domain.escrow.domain.EscrowFeeSettingsRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InMemoryFakeEscrowFeeSettingsRepository implements EscrowFeeSettingsRepository {

    private EscrowFeeSettings singleton;

    public InMemoryFakeEscrowFeeSettingsRepository() {
        try {
            var ctor = EscrowFeeSettings.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            singleton = ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        ReflectionTestUtils.setField(singleton, "id", EscrowFeeSettings.SINGLETON_ID);
        ReflectionTestUtils.setField(singleton, "commissionRate", new BigDecimal("0.0500"));
        ReflectionTestUtils.setField(singleton, "fuelPricePerL", 1650L);
        ReflectionTestUtils.setField(singleton, "baseFuelPrice", 1650L);
        ReflectionTestUtils.setField(singleton, "baseDeliveryFee", 1500L);
        ReflectionTestUtils.setField(singleton, "baseKmRate", 500L);
        ReflectionTestUtils.setField(singleton, "fuelEfficiency", new BigDecimal("25.00"));
        ReflectionTestUtils.setField(singleton, "minDeliveryFee", 3000L);
        ReflectionTestUtils.setField(singleton, "truckBaseDeliveryFee", 5000L);
        ReflectionTestUtils.setField(singleton, "truckBaseKmRate", 1200L);
        ReflectionTestUtils.setField(singleton, "truckFuelEfficiency", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(singleton, "truckMinDeliveryFee", 15000L);
        ReflectionTestUtils.setField(singleton, "updatedAt", LocalDateTime.now());
    }

    @Override
    public EscrowFeeSettings findSingleton() {
        return singleton;
    }

    @Override
    public EscrowFeeSettings save(EscrowFeeSettings settings) {
        this.singleton = settings;
        return settings;
    }
}
