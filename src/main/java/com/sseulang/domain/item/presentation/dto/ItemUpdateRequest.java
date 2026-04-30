package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemUpdateCommand;
import com.sseulang.domain.item.domain.RentalUnit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "물품 수정 요청. tradeType 은 변경 불가 — 거래 흐름 일관성 위해 등록 시점에 고정.")
public record ItemUpdateRequest(
        @Schema(description = "카테고리 id", example = "3", nullable = true)
        Long categoryId,

        @Schema(description = "제목", example = "아이폰 15 프로 256GB (가격 인하)", maxLength = 200)
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "상세 설명", example = "가격 5만원 인하했습니다.")
        @NotBlank String description,

        @Schema(description = "가격", example = "800000")
        @NotNull @PositiveOrZero Long price,

        @Schema(description = "보증금 (대여 거래)", example = "100000", nullable = true)
        @PositiveOrZero Long deposit,

        @Schema(description = "대여 단위", example = "DAY", nullable = true)
        RentalUnit rentalUnit,

        @Schema(description = "지역", example = "서울 강남구", maxLength = 100, nullable = true)
        @Size(max = 100) String region,

        @Schema(description = "이미지 URL 목록", nullable = true)
        List<String> imageUrls,

        @Schema(description = "해시태그", nullable = true)
        List<String> hashtags
) {
    public ItemUpdateCommand toCommand() {
        return new ItemUpdateCommand(
                categoryId, title, description, price, deposit, rentalUnit, region, imageUrls, hashtags
        );
    }
}
