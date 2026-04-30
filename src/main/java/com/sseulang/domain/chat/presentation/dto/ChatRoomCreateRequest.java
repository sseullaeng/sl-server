package com.sseulang.domain.chat.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Item 기준 1:1 채팅방 개설/조회. 동일 (buyer, item) 재요청 시 기존 채팅방 반환 (멱등).")
public record ChatRoomCreateRequest(
        @Schema(description = "채팅 대상 itemId", example = "42")
        @NotNull @Positive Long itemId
) { }
