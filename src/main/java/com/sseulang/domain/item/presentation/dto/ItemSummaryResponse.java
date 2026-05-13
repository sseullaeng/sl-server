package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemSummaryResult;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.DepositType;
import com.sseulang.domain.item.domain.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Schema(description = "물품 목록 row — 검색·찜·내물품 응답 공통. V25 라운드 12 PR-D 잔여 — 판매/대여 가격 분리.")
public record ItemSummaryResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "100") Long sellerId,
        @Schema(example = "5") Long categoryId,
        @Schema(example = "아이폰 14 Pro 미개봉") String title,
        @Schema(example = "1200000", description = "[DEPRECATED] primary 모드 가격 — 신규는 salePrice/rentalPrice 사용")
        long price,
        @Schema(example = "1200000", description = "판매가 (판매 모드 시)", nullable = true)
        Long salePrice,
        @Schema(example = "20000", description = "대여가 (대여 모드 시, rentalUnit 당)", nullable = true)
        Long rentalPrice,
        @Schema(example = "AMOUNT", description = "보증금 입력 타입 (대여 모드만)", nullable = true)
        DepositType depositType,
        @Schema(description = "[DEPRECATED] primary 모드 — 신규는 tradeTypes 사용")
        TradeType tradeType,
        @Schema(description = "거래 모드 set (판매/대여/나눔 복수 가능)", example = "[\"판매\", \"대여\"]")
        Set<TradeType> tradeTypes,
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
        @Schema(example = "127", description = "조회 수 (라운드 12 PR-D)")
        int viewCount,
        @Schema(description = "해시태그 (소문자 정규화 X — 등록한 그대로)", example = "[\"애플\",\"중고폰\"]")
        List<String> hashtags,
        LocalDateTime createdAt
) {
    public static ItemSummaryResponse from(ItemSummaryResult r) {
        return new ItemSummaryResponse(
                r.id(), r.sellerId(), r.categoryId(),
                r.title(), r.price(),
                r.salePrice(), r.rentalPrice(),
                r.depositType(),
                r.tradeType(), r.tradeTypes(),
                r.status(), r.region(),
                r.thumbnailUrl(), r.wishlistCount(), r.isWishlisted(),
                r.viewCount(),
                r.hashtags() == null ? List.of() : r.hashtags(),
                r.createdAt()
        );
    }
}
