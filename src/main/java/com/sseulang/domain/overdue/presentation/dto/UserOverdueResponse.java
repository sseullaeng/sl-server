package com.sseulang.domain.overdue.presentation.dto;

import com.sseulang.domain.overdue.domain.OverduePhase;
import com.sseulang.domain.overdue.domain.OverdueRecord;
import com.sseulang.domain.overdue.domain.OverdueStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "본인용 — 연체 record 단건 (민감 정보 제외).")
public record UserOverdueResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "100") Long escrowApplicationId,
        @Schema(example = "100000") long depositAmount,
        @Schema(example = "30000") long depositForfeitedAmount,
        @Schema(example = "20000") long extraDebtAmount,
        @Schema(description = "잔여 보증금 = depositAmount - depositForfeitedAmount") long remainingDeposit,
        @Schema(example = "8") int overdueDays,
        OverduePhase phase,
        OverdueStatus status,
        LocalDateTime rentalEndAt,
        LocalDateTime overdueStartedAt,
        @Schema(nullable = true) LocalDateTime accountSuspendedAt,
        @Schema(nullable = true) LocalDateTime resolvedAt,
        LocalDateTime createdAt
) {
    public static UserOverdueResponse from(OverdueRecord r) {
        return new UserOverdueResponse(
                r.getId(),
                r.getEscrowApplicationId(),
                r.getDepositAmount(),
                r.getDepositForfeitedAmount(),
                r.getExtraDebtAmount(),
                r.remainingDepositAmount(),
                r.getOverdueDays(),
                r.getPhase(),
                r.getStatus(),
                r.getRentalEndAt(),
                r.getOverdueStartedAt(),
                r.getAccountSuspendedAt(),
                r.getResolvedAt(),
                r.getCreatedAt()
        );
    }
}
