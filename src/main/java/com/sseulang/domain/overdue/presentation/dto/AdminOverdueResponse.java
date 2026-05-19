package com.sseulang.domain.overdue.presentation.dto;

import com.sseulang.domain.overdue.domain.OverdueLegalAction;
import com.sseulang.domain.overdue.domain.OverduePhase;
import com.sseulang.domain.overdue.domain.OverdueRecord;
import com.sseulang.domain.overdue.domain.OverdueStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "관리자 — 연체 단건 응답.")
public record AdminOverdueResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "100") Long escrowApplicationId,
        @Schema(example = "11") Long buyerId,
        @Schema(example = "20") Long sellerId,
        @Schema(example = "100000") long depositAmount,
        @Schema(example = "30000") long depositForfeitedAmount,
        @Schema(example = "20000") long extraDebtAmount,
        @Schema(description = "잔여 보증금 = depositAmount - depositForfeitedAmount") long remainingDeposit,
        @Schema(example = "8") int overdueDays,
        OverduePhase phase,
        OverdueStatus status,
        OverdueLegalAction legalAction,
        LocalDateTime rentalEndAt,
        LocalDateTime overdueStartedAt,
        @Schema(nullable = true) LocalDateTime accountSuspendedAt,
        @Schema(nullable = true) LocalDateTime resolvedAt,
        @Schema(nullable = true) String resolutionNote,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminOverdueResponse from(OverdueRecord r) {
        return new AdminOverdueResponse(
                r.getId(),
                r.getEscrowApplicationId(),
                r.getBuyerId(),
                r.getSellerId(),
                r.getDepositAmount(),
                r.getDepositForfeitedAmount(),
                r.getExtraDebtAmount(),
                r.remainingDepositAmount(),
                r.getOverdueDays(),
                r.getPhase(),
                r.getStatus(),
                r.getLegalAction(),
                r.getRentalEndAt(),
                r.getOverdueStartedAt(),
                r.getAccountSuspendedAt(),
                r.getResolvedAt(),
                r.getResolutionNote(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}
