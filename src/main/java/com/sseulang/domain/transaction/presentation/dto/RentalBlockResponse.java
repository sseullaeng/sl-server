package com.sseulang.domain.transaction.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "물품의 활성 대여 거래 기간 목록 — 달력 비활성화용. 취소/거래완료 제외, 채팅중/예약/인계완료 만 포함.")
public record RentalBlockResponse(
        @Schema(description = "예약된 기간 페어 — 빈 배열이면 모두 가용")
        List<Block> blocks
) {
    public record Block(
            @Schema(example = "2026-05-20T00:00:00") LocalDateTime start,
            @Schema(example = "2026-05-25T00:00:00") LocalDateTime end
    ) {}
}
