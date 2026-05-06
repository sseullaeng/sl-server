package com.sseulang.domain.escrow.application;

import com.sseulang.domain.escrow.domain.EscrowApplicationRepository;
import com.sseulang.domain.escrow.domain.EscrowFeeSettings;
import com.sseulang.domain.escrow.domain.EscrowFeeSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 거래대행 수수료 정책 운영 (admin). 결정 #9 + #12.
 *
 * <p>변경 흐름: PATCH 호출 → DB UPDATE → 진행 중 application 은 snapshot 으로 영향 X.
 * 변경 시 진행 중 N건 표시 (12-a HH2) 위해 countInProgress 동시 반환.</p>
 */
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

    /** admin 변경 시 표시용 — 진행 중 application N건. */
    public long countInProgress() {
        return applicationRepository.countInProgress();
    }
}
