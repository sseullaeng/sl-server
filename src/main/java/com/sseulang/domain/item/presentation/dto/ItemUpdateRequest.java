package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemUpdateCommand;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Schema(description = "물품 수정 요청. tradeTypes 로 모드 변경 가능.")
public record ItemUpdateRequest(
        @Schema(description = "카테고리 id", example = "3", nullable = true)
        Long categoryId,

        @Schema(description = "제목", example = "아이폰 15 프로 256GB (가격 인하)", maxLength = 200)
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "상세 설명", example = "가격 5만원 인하했습니다.")
        @NotBlank String description,

        @Schema(description = "거래 유형 set (1개 이상)", example = "[\"판매\", \"대여\"]")
        Set<TradeType> tradeTypes,

        @Schema(description = "판매가 (tradeTypes 에 판매 포함 시 필수)", example = "800000", nullable = true)
        @PositiveOrZero Long salePrice,

        @Schema(description = "대여가 (tradeTypes 에 대여 포함 시 필수)", example = "18000", nullable = true)
        @PositiveOrZero Long rentalPrice,

        @Schema(description = "보증금 (대여 거래)", example = "100000", nullable = true)
        @PositiveOrZero Long deposit,

        @Schema(description = "대여 단위", example = "일", nullable = true)
        RentalUnit rentalUnit,

        
        @Schema(description = "[DEPRECATED] 단일 가격 — backwards compat 만.", example = "800000", nullable = true)
        @PositiveOrZero Long price,
        @Schema(description = "[DEPRECATED] 단일 거래 유형 — backwards compat 만.", example = "판매", nullable = true)
        TradeType tradeType,

        @Schema(description = "지역", example = "서울 강남구", maxLength = 100, nullable = true)
        @Size(max = 100) String region,

        @Schema(description = "이미지 URL 목록", nullable = true)
        List<String> imageUrls,

        @Schema(description = "해시태그", nullable = true)
        List<String> hashtags
) {
    public ItemUpdateCommand toCommand() {
        Set<TradeType> resolvedTypes = resolveTradeTypes();
        Long resolvedSale = salePrice != null ? salePrice
                : (tradeType == TradeType.판매 ? price : null);
        Long resolvedRental = rentalPrice != null ? rentalPrice
                : (tradeType == TradeType.대여 ? price : null);
        return new ItemUpdateCommand(
                categoryId, title, description,
                resolvedTypes, resolvedSale, resolvedRental,
                deposit, rentalUnit, region, imageUrls, hashtags
        );
    }

    @jakarta.validation.constraints.AssertTrue(message = "tradeTypes 또는 tradeType 중 하나는 필수입니다")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isModeSpecified() {
        return (tradeTypes != null && !tradeTypes.isEmpty()) || tradeType != null;
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
