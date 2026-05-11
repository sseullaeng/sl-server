package com.sseulang.domain.chat.presentation.dto;

import com.sseulang.domain.item.domain.TradeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Item 기준 1:1 채팅방 개설/조회. 라운드 12 PR-C — 같은 (buyer, item) 라도 tradeMode 다르면 별도 채팅방. "
        + "tradeMode null 이면 item.tradeType 으로 자동 채움 (단일 mode 아이템 호환).")
public record ChatRoomCreateRequest(
        @Schema(description = "채팅 대상 itemId", example = "42")
        @NotNull @Positive Long itemId,

        @Schema(description = "거래방식 — 판매 | 대여 | 나눔. null 이면 item.tradeType 자동 채움.",
                example = "판매", nullable = true)
        TradeType tradeMode
) { }
