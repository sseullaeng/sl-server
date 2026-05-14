package com.sseulang.domain.escrow.application.dto;

import com.sseulang.domain.escrow.domain.EscrowApplication;
import com.sseulang.domain.escrow.domain.EscrowApplicationStatus;

import java.time.LocalDateTime;

public record EscrowOverdueSnapshot(
        Long id,
        Long buyerId,
        Long sellerId,
        Long depositAmount,
        LocalDateTime rentalEndAt,
        EscrowApplicationStatus status,
        boolean rentalMode
) {
    public static EscrowOverdueSnapshot from(EscrowApplication app) {
        return new EscrowOverdueSnapshot(
                app.getId(),
                app.getBuyerId(),
                app.getSellerId(),
                app.getDepositAmount(),
                app.getRentalEndAt(),
                app.getStatus(),
                app.isRentalMode()
        );
    }
}
