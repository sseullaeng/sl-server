package com.sseulang.domain.review.presentation.dto;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.transaction.application.dto.PendingReviewableResult;

import java.time.LocalDateTime;

/**
 * Review 작성 대기 거래 응답 (follow-up #56).
 *
 * <p>{@code deadline = completedAt + 7d} — 클라이언트가 남은 시간 카운트다운 UI 에 직접 사용.</p>
 */
public record PendingReviewResponse(
        Long transactionId,
        Long itemId,
        Long revieweeId,
        TradeType tradeType,
        long price,
        LocalDateTime completedAt,
        LocalDateTime deadline
) {
    public static PendingReviewResponse from(PendingReviewableResult r) {
        return new PendingReviewResponse(
                r.transactionId(),
                r.itemId(),
                r.revieweeId(),
                r.tradeType(),
                r.price(),
                r.completedAt(),
                r.deadline()
        );
    }
}
