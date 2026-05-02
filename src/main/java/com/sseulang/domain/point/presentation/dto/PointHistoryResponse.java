package com.sseulang.domain.point.presentation.dto;

import com.sseulang.domain.point.application.dto.PointHistoryResult;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.domain.PointReferenceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "포인트 히스토리 row — 마이페이지 잔액 변동 내역. amount 는 부호 포함 (+ 적립, - 차감).")
public record PointHistoryResponse(
        @Schema(example = "200") Long id,
        @Schema(description = "변동 타입 — 충전/결제/판매정산/출금/환불/배달결제/배달정산") PointHistoryType type,
        @Schema(example = "50000", description = "+ 적립 / - 차감 부호 포함") long amount,
        @Schema(example = "80000", description = "변동 직후 잔액 스냅샷") long balanceAfter,
        @Schema(description = "참조 도메인 — PAYMENT/TRANSACTION/WITHDRAWAL/DELIVERY/REFUND") PointReferenceType referenceType,
        @Schema(example = "78", description = "참조 도메인 row id (예 paymentId, transactionId)") Long referenceId,
        @Schema(example = "토스 충전") String description,
        LocalDateTime createdAt
) {
    public static PointHistoryResponse from(PointHistoryResult r) {
        return new PointHistoryResponse(
                r.id(), r.type(),
                r.amount(), r.balanceAfter(),
                r.referenceType(), r.referenceId(),
                r.description(), r.createdAt()
        );
    }
}
