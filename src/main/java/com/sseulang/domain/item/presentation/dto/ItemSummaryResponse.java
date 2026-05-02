package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemSummaryResult;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "물품 목록 row — 검색·찜·내물품 응답 공통.")
public record ItemSummaryResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "100") Long sellerId,
        @Schema(example = "5") Long categoryId,
        @Schema(example = "아이폰 14 Pro 미개봉") String title,
        @Schema(example = "1200000") long price,
        TradeType tradeType,
        ItemStatus status,
        @Schema(example = "서울 강남구") String region,
        @Schema(example = "https://cdn.sseulang.com/items/42/abc.jpg",
                description = "썸네일 1장 URL — 등록된 이미지가 없으면 null")
        String thumbnailUrl,
        @Schema(example = "8", description = "찜 누적 카운트")
        int wishlistCount,
        @Schema(example = "false",
                description = "본인 찜 여부 — 비로그인은 항상 false")
        boolean isWishlisted,
        LocalDateTime createdAt
) {
    public static ItemSummaryResponse from(ItemSummaryResult r) {
        return new ItemSummaryResponse(
                r.id(), r.sellerId(), r.categoryId(),
                r.title(), r.price(),
                r.tradeType(), r.status(), r.region(),
                r.thumbnailUrl(), r.wishlistCount(), r.isWishlisted(),
                r.createdAt()
        );
    }
}
