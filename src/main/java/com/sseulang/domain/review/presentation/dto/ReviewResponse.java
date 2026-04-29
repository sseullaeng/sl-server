package com.sseulang.domain.review.presentation.dto;

import com.sseulang.domain.review.application.dto.ReviewResult;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long transactionId,
        Long reviewerId,
        Long revieweeId,
        int rating,
        String comment,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(ReviewResult r) {
        return new ReviewResponse(
                r.id(), r.transactionId(),
                r.reviewerId(), r.revieweeId(),
                r.rating(), r.comment(),
                r.createdAt()
        );
    }
}
