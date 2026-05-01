package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemSummaryResult;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "물품 목록 row — 검색/페이징 응답에 포함.")
public record ItemSummaryResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "100") Long sellerId,
        @Schema(example = "5") Long categoryId,
        @Schema(example = "아이폰 14 Pro 미개봉") String title,
        @Schema(example = "1200000") long price,
        TradeType tradeType,
        ItemStatus status,
        @Schema(example = "서울 강남구") String region,
        LocalDateTime createdAt
) {
    public static ItemSummaryResponse from(ItemSummaryResult r) {
        return new ItemSummaryResponse(
                r.id(), r.sellerId(), r.categoryId(),
                r.title(), r.price(),
                r.tradeType(), r.status(), r.region(),
                r.createdAt()
        );
    }
}
