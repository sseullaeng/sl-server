package com.sseulang.domain.escrow.domain;

public interface EscrowFeeSettingsRepository {

    /** Singleton row (id=1) 조회. 없으면 IllegalStateException — V15 마이그레이션이 default 보장. */
    EscrowFeeSettings findSingleton();

    EscrowFeeSettings save(EscrowFeeSettings settings);
}
