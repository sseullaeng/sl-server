package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemDetailResult;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Schema(description = "물품 상세 — 이미지/해시태그 포함. GET /items/{id} 호출 시 viewCount 1 증가. "
        + "V25 라운드 12 PR-D 잔여 — salePrice/rentalPrice/tradeTypes 분리.")
public record ItemDetailResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "100") Long sellerId,
        @Schema(example = "5", description = "카테고리 id (없으면 null)") Long categoryId,
        @Schema(example = "아이폰 14 Pro 미개봉") String title,
        @Schema(example = "박스 미개봉, 색상 딥퍼플") String description,
        @Schema(example = "1200000", description = "[DEPRECATED] primary 가격 — 신규는 salePrice/rentalPrice")
        long price,
        @Schema(example = "1200000", description = "판매가 (판매 모드 시)", nullable = true)
        Long salePrice,
        @Schema(example = "20000", description = "대여가 (대여 모드 시, rentalUnit 당)", nullable = true)
        Long rentalPrice,
        @Schema(example = "100000", description = "보증금 (대여 모드 시 필수)") Long deposit,
        @Schema(description = "대여 단위 — 시간/일/주/월") RentalUnit rentalUnit,
        @Schema(description = "[DEPRECATED] primary 모드 — 신규는 tradeTypes 사용") TradeType tradeType,
        @Schema(description = "거래 모드 set (판매/대여/나눔 복수 가능)", example = "[\"판매\", \"대여\"]")
        Set<TradeType> tradeTypes,
        ItemStatus status,
        @Schema(example = "서울 강남구") String region,
        @Schema(example = "127") int viewCount,
        @Schema(example = "8") int wishlistCount,
        List<ItemImageResponse> images,
        @Schema(example = "[\"아이폰\",\"미개봉\"]") List<String> hashtags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ItemDetailResponse from(ItemDetailResult r) {
        return new ItemDetailResponse(
                r.id(), r.sellerId(), r.categoryId(),
                r.title(), r.description(),
                r.price(), r.salePrice(), r.rentalPrice(),
                r.deposit(), r.rentalUnit(),
                r.tradeType(), r.tradeTypes(),
                r.status(), r.region(),
                r.viewCount(), r.wishlistCount(),
                r.images().stream().map(ItemImageResponse::from).toList(),
                r.hashtags(),
                r.createdAt(), r.updatedAt()
        );
    }
}
