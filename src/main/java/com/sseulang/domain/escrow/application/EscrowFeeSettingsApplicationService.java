package com.sseulang.domain.escrow.application;

import com.sseulang.domain.escrow.domain.EscrowApplicationRepository;
import com.sseulang.domain.escrow.domain.EscrowFeeSettings;
import com.sseulang.domain.escrow.domain.EscrowFeeSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
public class EscrowFeeSettingsApplicationService {

    private final EscrowFeeSettingsRepository repository;
    private final EscrowApplicationRepository applicationRepository;

    public EscrowFeeSettingsApplicationService(
            EscrowFeeSettingsRepository repository,
            EscrowApplicationRepository applicationRepository
    ) {
        this.repository = repository;
        this.applicationRepository = applicationRepository;
    }

    public EscrowFeeSettings get() {
        return repository.findSingleton();
    }

    @Transactional
    public EscrowFeeSettings update(
            BigDecimal commissionRate,
            long fuelPricePerL, long baseFuelPrice,
            long baseDeliveryFee, long baseKmRate, BigDecimal fuelEfficiency, long minDeliveryFee,
            long truckBaseDeliveryFee, long truckBaseKmRate, BigDecimal truckFuelEfficiency, long truckMinDeliveryFee,
            Long adminId
    ) {
        EscrowFeeSettings settings = repository.findSingleton();
        settings.apply(
                commissionRate,
                fuelPricePerL, baseFuelPrice,
                baseDeliveryFee, baseKmRate, fuelEfficiency, minDeliveryFee,
                truckBaseDeliveryFee, truckBaseKmRate, truckFuelEfficiency, truckMinDeliveryFee,
                adminId
        );
        return repository.save(settings);
    }

    
    public long countInProgress() {
        return applicationRepository.countInProgress();
    }
}
