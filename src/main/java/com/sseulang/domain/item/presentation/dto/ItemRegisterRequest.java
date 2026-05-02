package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "물품 등록 요청. tradeType=대여 인 경우 deposit/rentalUnit 권장.")
public record ItemRegisterRequest(
        @Schema(description = "카테고리 id (선택)", example = "3")
        Long categoryId,

        @Schema(description = "제목", example = "아이폰 15 프로 256GB", maxLength = 200)
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "상세 설명", example = "구매 1년차, 박스/충전기 풀구성. 직거래 선호.")
        @NotBlank String description,

        @Schema(description = "가격 (원). 나눔이면 0", example = "850000")
        @NotNull @PositiveOrZero Long price,

        @Schema(description = "보증금 (대여 거래 전용, 그 외 null)", example = "100000", nullable = true)
        @PositiveOrZero Long deposit,

        @Schema(description = "대여 단위 (대여 거래 전용) — 시간/일/주/월", example = "일",
                allowableValues = {"시간", "일", "주", "월"}, nullable = true)
        RentalUnit rentalUnit,

        @Schema(description = "거래 유형", example = "판매",
                allowableValues = {"판매", "대여", "나눔"})
        @NotNull TradeType tradeType,

        @Schema(description = "지역", example = "서울 강남구", maxLength = 100, nullable = true)
        @Size(max = 100) String region,

        @Schema(description = "이미지 URL 목록 (최대 5장, S3 presigned URL 업로드 후 키)",
                example = "[\"https://cdn.sseulang.test/items/abc.jpg\"]", nullable = true)
        List<String> imageUrls,

        @Schema(description = "해시태그 (선택)", example = "[\"애플\",\"중고폰\"]", nullable = true)
        List<String> hashtags
) {
    public ItemRegisterCommand toCommand(Long sellerId) {
        return new ItemRegisterCommand(
                sellerId, categoryId, title, description,
                price, deposit, rentalUnit, tradeType, region, imageUrls, hashtags
        );
    }
}
