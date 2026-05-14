package com.sseulang.domain.overdue.application.dto;

import com.sseulang.domain.overdue.domain.OverduePhase;
import com.sseulang.domain.overdue.domain.OverdueStatus;

public record OverdueChargeQueryResult(
        Long overdueRecordId,
        Long escrowApplicationId,
        Long buyerId,
        long depositAmount,
        int overdueDays,
        OverduePhase phase,
        OverdueStatus status,
        long depositForfeitedAmount,
        long remainingDepositAmount,
        long extraDebtAmount
) {
}
