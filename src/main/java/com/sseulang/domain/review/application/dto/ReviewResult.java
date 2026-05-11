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
        LocalDateTime createdAt
) {
    public static ReviewResult from(Review r) {
        return new ReviewResult(
                r.getId(), r.getTransactionId(),
                r.getReviewerId(), r.getRevieweeId(),
                r.getRating(), r.getComment(),
                r.getCreatedAt()
        );
    }

    
    public ReviewResult masked(Long requesterId) {
        if (reviewerId.equals(requesterId)) {
            return this;
        }
        return new ReviewResult(id, transactionId, reviewerId, revieweeId, rating, null, createdAt);
    }
}
