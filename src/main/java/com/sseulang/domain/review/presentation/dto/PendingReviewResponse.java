package com.sseulang.domain.review.presentation.dto;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.domain.transaction.application.dto.PendingReviewableResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "리뷰 작성 대기 거래 — 본인이 reviewer 로 아직 작성 안 한 7일 이내 완료 거래.")
public record PendingReviewResponse(
        @Schema(example = "12") Long transactionId,
        @Schema(example = "42") Long itemId,
        @Schema(description = "상대방 (reviewee) id", example = "200") Long revieweeId,
        TradeType tradeType,
        @Schema(example = "1200000") long price,
        LocalDateTime completedAt,
        @Schema(description = "완료일 + 7일. 이 시간 지나면 작성 불가") LocalDateTime deadline,
        @Schema(example = "https://...", description = "리뷰 대상 거래의 물품 썸네일 URL", nullable = true)
        String itemThumbnailUrl
) {
    public static PendingReviewResponse from(PendingReviewableResult r) {
        return new PendingReviewResponse(
                r.transactionId(),
                r.itemId(),
                r.revieweeId(),
                r.tradeType(),
                r.price(),
                r.completedAt(),
                r.deadline(),
                r.itemThumbnailUrl()
        );
    }
}
