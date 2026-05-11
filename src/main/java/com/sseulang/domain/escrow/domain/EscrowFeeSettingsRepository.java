package com.sseulang.domain.escrow.domain;

public interface EscrowFeeSettingsRepository {

    
    EscrowFeeSettings findSingleton();

    EscrowFeeSettings save(EscrowFeeSettings settings);
}
