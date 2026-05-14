package com.sseulang.domain.review.presentation.dto;

import com.sseulang.domain.review.application.dto.ReviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "리뷰 — 거래 완료 후 7일 이내 작성. 한줄평(comment) 노출 정책: "
        + "(1) 작성자/대상자 본인 → 항상 원본 + contentVisible flag, "
        + "(2) 제3자 → contentVisible=true 면 원본, false 면 null 마스킹.")
public record ReviewResponse(
        @Schema(example = "9") Long id,
        @Schema(example = "12") Long transactionId,
        @Schema(example = "200") Long reviewerId,
        @Schema(example = "100") Long revieweeId,
        @Schema(example = "5", description = "1~5 — 항상 공개") int rating,
        @Schema(example = "친절하고 빠른 거래", description = "한줄평. 비공개 처리됐고 본인이 아니면 null", nullable = true) String comment,
        @Schema(example = "true", description = "대상자(reviewee)가 토글한 공개 상태. 마스킹된 응답이라도 이 flag 는 원본 그대로 — 본인이 자기 페이지에서 토글 UI 표시용") boolean contentVisible,
        @Schema(example = "https://...", description = "리뷰 대상 거래의 물품 썸네일 URL", nullable = true)
        String itemThumbnailUrl,
        LocalDateTime createdAt
) {
    public static ReviewResponse from(ReviewResult r) {
        return new ReviewResponse(
                r.id(), r.transactionId(),
                r.reviewerId(), r.revieweeId(),
                r.rating(), r.comment(),
                r.contentVisible(),
                r.itemThumbnailUrl(),
                r.createdAt()
        );
    }
}
