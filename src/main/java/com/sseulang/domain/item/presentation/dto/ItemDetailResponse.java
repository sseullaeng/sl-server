package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemDetailResult;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.RentalUnit;
import com.sseulang.domain.item.domain.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "물품 상세 — 이미지/해시태그 포함. GET /items/{id} 호출 시 viewCount 1 증가.")
public record ItemDetailResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "100") Long sellerId,
        @Schema(example = "5", description = "카테고리 id (없으면 null)") Long categoryId,
        @Schema(example = "아이폰 14 Pro 미개봉") String title,
        @Schema(example = "박스 미개봉, 색상 딥퍼플") String description,
        @Schema(example = "1200000") long price,
        @Schema(example = "100000", description = "대여 시 보증금 (판매/나눔은 null)") Long deposit,
        @Schema(description = "대여 단위 (판매/나눔은 null)") RentalUnit rentalUnit,
        TradeType tradeType,
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
                r.price(), r.deposit(), r.rentalUnit(), r.tradeType(),
                r.status(), r.region(),
                r.viewCount(), r.wishlistCount(),
                r.images().stream().map(ItemImageResponse::from).toList(),
                r.hashtags(),
                r.createdAt(), r.updatedAt()
        );
    }
}
