package com.sseulang.domain.escrow.infrastructure.persistence;

import com.sseulang.domain.escrow.domain.EscrowFeeSettings;
import com.sseulang.domain.escrow.domain.EscrowFeeSettingsRepository;
import org.springframework.stereotype.Repository;

@Repository
public class EscrowFeeSettingsRepositoryImpl implements EscrowFeeSettingsRepository {

    private final EscrowFeeSettingsJpaRepository jpa;

    public EscrowFeeSettingsRepositoryImpl(EscrowFeeSettingsJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public EscrowFeeSettings findSingleton() {
        return jpa.findById(EscrowFeeSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "escrow_fee_settings singleton row missing — V15 마이그레이션 확인"));
    }

    @Override
    public EscrowFeeSettings save(EscrowFeeSettings settings) {
        return jpa.save(settings);
    }
}
