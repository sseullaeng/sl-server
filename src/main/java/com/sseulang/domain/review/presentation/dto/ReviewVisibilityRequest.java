package com.sseulang.domain.review.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "리뷰 한줄평 공개여부 토글. 대상자(reviewee)만 호출 가능. 별점 자체는 항상 공개 — 텍스트(comment)만 마스킹.")
public record ReviewVisibilityRequest(
        @Schema(description = "true=한줄평 공개 / false=한줄평 마스킹(null 응답)", example = "false")
        @NotNull Boolean contentVisible
) {
}
