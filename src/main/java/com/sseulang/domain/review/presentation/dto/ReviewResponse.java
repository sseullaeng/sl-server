package com.sseulang.domain.review.presentation.dto;

import com.sseulang.domain.review.application.dto.ReviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "리뷰 — 거래 완료 후 7일 이내 작성. 한줄평(comment)은 작성자 본인에게만 노출 (그 외엔 마스킹).")
public record ReviewResponse(
        @Schema(example = "9") Long id,
        @Schema(example = "12") Long transactionId,
        @Schema(example = "200") Long reviewerId,
        @Schema(example = "100") Long revieweeId,
        @Schema(example = "5", description = "1~5") int rating,
        @Schema(example = "친절하고 빠른 거래", description = "한줄평. 작성자 본인 외엔 null 마스킹") String comment,
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
