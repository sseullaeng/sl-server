package com.sseulang.domain.escrow.infrastructure.persistence;

import com.sseulang.domain.escrow.domain.EscrowFeeSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EscrowFeeSettingsJpaRepository extends JpaRepository<EscrowFeeSettings, Long> {
}
