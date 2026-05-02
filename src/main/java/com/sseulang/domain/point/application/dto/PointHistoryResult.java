package com.sseulang.domain.point.application.dto;

import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;

import java.time.LocalDateTime;

/**
 * 본인 포인트 히스토리 row — 페이징 응답에 포함. Application/Presentation 경계 DTO.
 *
 * <p>amount 는 부호 포함 (충전/적립 = +, 결제/출금 = -). balanceAfter 는 변동 직후 잔액 스냅샷.</p>
 */
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
