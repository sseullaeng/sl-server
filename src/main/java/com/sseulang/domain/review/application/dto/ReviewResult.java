package com.sseulang.domain.review.application.dto;

import com.sseulang.domain.review.domain.Review;

import java.time.LocalDateTime;

public record ReviewResult(
        Long id,
        Long transactionId,
        Long reviewerId,
        Long revieweeId,
        int rating,
        String comment,
        boolean contentVisible,
        LocalDateTime createdAt,
        String itemThumbnailUrl
) {
    public static ReviewResult from(Review r) {
        return new ReviewResult(
                r.getId(), r.getTransactionId(),
                r.getReviewerId(), r.getRevieweeId(),
                r.getRating(), r.getComment(),
                r.isContentVisible(),
                r.getCreatedAt(),
                null
        );
    }

    public static ReviewResult from(Review r, String itemThumbnailUrl) {
        return new ReviewResult(
                r.getId(), r.getTransactionId(),
                r.getReviewerId(), r.getRevieweeId(),
                r.getRating(), r.getComment(),
                r.isContentVisible(),
                r.getCreatedAt(),
                itemThumbnailUrl
        );
    }

    // 작성자/대상자 본인 → comment 원본. 그 외 → contentVisible=false 면 null 마스킹, true 면 원본.
    public ReviewResult masked(Long requesterId) {
        boolean isParty = requesterId != null
                && (reviewerId.equals(requesterId) || revieweeId.equals(requesterId));
        if (isParty || contentVisible) {
            return this;
        }
        return new ReviewResult(id, transactionId, reviewerId, revieweeId, rating, null, contentVisible, createdAt, itemThumbnailUrl);
    }
}
