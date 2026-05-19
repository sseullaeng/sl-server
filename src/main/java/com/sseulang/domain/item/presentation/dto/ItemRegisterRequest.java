package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.domain.DepositType;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Schema(description = "물품 등록 요청. tradeTypes 에 판매/대여/나눔 복수 지정 가능 (예: [판매, 대여]).")
public record ItemRegisterRequest(
        @Schema(description = "카테고리 id (선택)", example = "3")
        Long categoryId,

        @Schema(description = "제목", example = "아이폰 15 프로 256GB", maxLength = 200)
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "상세 설명", example = "구매 1년차, 박스/충전기 풀구성. 직거래 선호.")
        @NotBlank String description,

        @Schema(description = "거래 유형 set (1개 이상). 판매/대여 동시 등록 가능.",
                example = "[\"판매\", \"대여\"]")
        Set<TradeType> tradeTypes,

        @Schema(description = "판매가 (tradeTypes 에 판매 포함 시 필수)", example = "850000", nullable = true)
        @PositiveOrZero Long salePrice,

        @Schema(description = "대여가 (tradeTypes 에 대여 포함 시 필수, rentalUnit 당)", example = "20000", nullable = true)
        @PositiveOrZero Long rentalPrice,

        @Schema(description = "보증금 (대여 모드 시 필수)", example = "100000", nullable = true)
        @PositiveOrZero Long deposit,

        @Schema(description = "보증금 입력 타입 (대여 거래 전용)", example = "AMOUNT", allowableValues = {"AMOUNT", "PERCENT"}, nullable = true)
        DepositType depositType,

        @Schema(description = "대여 단위 — 시간/일/주/월 (대여 모드 시 필수)", example = "일",
                allowableValues = {"시간", "일", "주", "월"}, nullable = true)
        RentalUnit rentalUnit,

        
        @Schema(description = "[DEPRECATED] 단일 가격 — tradeTypes/sale/rental 로 마이그레이션. backwards compat 만.",
                example = "850000", nullable = true)
        @PositiveOrZero Long price,

        @Schema(description = "[DEPRECATED] 단일 거래 유형 — tradeTypes 로 마이그레이션. backwards compat 만.",
                example = "판매", allowableValues = {"판매", "대여", "나눔"}, nullable = true)
        TradeType tradeType,

        @Schema(description = "지역", example = "서울 강남구", maxLength = 100, nullable = true)
        @Size(max = 100) String region,

        @Schema(description = "이미지 URL 목록 (최대 10장, S3 presigned URL 업로드 후 키)",
                example = "[\"https://cdn.sseulang.test/items/abc.jpg\"]", nullable = true)
        List<String> imageUrls,

        @Schema(description = "해시태그 (선택, 최대 10개, 태그당 50자)", example = "[\"애플\",\"중고폰\"]", nullable = true)
        @Size(max = 10, message = "해시태그는 최대 10개까지 등록할 수 있습니다")
        List<@Size(max = 50, message = "태그는 50자 이하여야 합니다") String> hashtags
) {
    public ItemRegisterCommand toCommand(Long sellerId) {
        Set<TradeType> resolvedTypes = resolveTradeTypes();
        Long resolvedSale = salePrice != null ? salePrice
                : (tradeType == TradeType.판매 ? price : null);
        Long resolvedRental = rentalPrice != null ? rentalPrice
                : (tradeType == TradeType.대여 ? price : null);
        return new ItemRegisterCommand(
                sellerId, categoryId, title, description,
                resolvedTypes, resolvedSale, resolvedRental,
                deposit, depositType, rentalUnit, region, imageUrls, hashtags
        );
    }

    @jakarta.validation.constraints.AssertTrue(message = "tradeTypes 또는 tradeType 중 하나는 필수입니다")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isModeSpecified() {
        return (tradeTypes != null && !tradeTypes.isEmpty()) || tradeType != null;
    }

    @AssertTrue(message = "대여 거래는 depositType 이 필수입니다")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isDepositTypeValid() {
        return !resolveTradeTypes().contains(TradeType.대여) || depositType != null;
    }

    private Set<TradeType> resolveTradeTypes() {
        if (tradeTypes != null && !tradeTypes.isEmpty()) {
            return EnumSet.copyOf(tradeTypes);
        }
        if (tradeType != null) {
            Set<TradeType> s = new LinkedHashSet<>();
            s.add(tradeType);
            return s;
        }
        return new LinkedHashSet<>();
    }
}
