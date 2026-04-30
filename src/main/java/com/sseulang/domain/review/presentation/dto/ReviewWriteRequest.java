package com.sseulang.domain.review.presentation.dto;

import com.sseulang.domain.review.application.dto.ReviewWriteCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "거래 후기 작성. 거래완료 후 7일 내 양 당사자만 작성 가능. 별점 1~5 + 한줄평.")
public record ReviewWriteRequest(
        @Schema(description = "리뷰 대상 거래 id", example = "100")
        @NotNull @Positive Long transactionId,

        @Schema(description = "별점 (1~5)", example = "5", minimum = "1", maximum = "5")
        @Min(1) @Max(5) int rating,

        @Schema(description = "한줄평 (선택)", example = "거래 정확하시고 친절했습니다", maxLength = 500, nullable = true)
        @Size(max = 500) String comment
) {
    public ReviewWriteCommand toCommand(Long reviewerId) {
        return new ReviewWriteCommand(transactionId, reviewerId, rating, comment);
    }
}
