package com.sseulang.domain.transaction.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "대여 신청 — buyer 가 호출. status=채팅중 으로 Transaction 생성 → seller 가 [예약] 으로 수락.")
public record RentalRequestRequest(
        @Schema(example = "2026-05-20T00:00:00") @NotNull LocalDateTime rentalStart,
        @Schema(example = "2026-05-25T00:00:00") @NotNull LocalDateTime rentalEnd,
        @Schema(description = "채팅방 ID — 없으면 null. 협상 채팅 중에 신청하면 채팅방 매핑.", nullable = true)
        Long chatRoomId
) {
}
