package com.sseulang.domain.point.application.dto;

import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;

import java.time.LocalDateTime;

public record PointHistoryResult(
        Long id,
        PointHistoryType type,
        long amount,
        long balanceAfter,
        PointReferenceType referenceType,
        Long referenceId,
        String description,
        LocalDateTime createdAt
) {
    public static PointHistoryResult from(PointHistory h) {
        return new PointHistoryResult(
                h.getId(),
                h.getPointType(),
                h.getAmount(),
                h.getBalanceAfter(),
                h.getReferenceType(),
                h.getReferenceId(),
                h.getDescription(),
                h.getCreatedAt()
        );
    }
}
