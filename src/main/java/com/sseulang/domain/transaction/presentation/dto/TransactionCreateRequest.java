package com.sseulang.domain.transaction.presentation.dto;

import com.sseulang.domain.transaction.application.dto.TransactionCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

@Schema(description = "거래 생성 (판매자가 호출). 채팅중 상태로 시작. 라운드 12 (#3.2): chatRoomId 필수 — "
        + "거래는 채팅방 안에서만 시작 가능. 호출자 != item.sellerId 면 TX_SELLER_ONLY. "
        + "같은 채팅방에 진행 중 거래 있으면 TX_ALREADY_ACTIVE_IN_ROOM. "
        + "buyerId 는 chatRoom 의 상대방에서 백엔드가 도출. 대여 거래만 rentalStart/End 필요.")
public record TransactionCreateRequest(
        @Schema(description = "거래 대상 itemId", example = "42")
        @NotNull @Positive Long itemId,

        @Schema(description = "거래가 시작될 채팅방 ID — buyer/seller 가 참여자여야 하고, "
                + "chatRoom.itemId 가 요청 itemId 와 일치해야 함",
                example = "7")
        @NotNull @Positive Long chatRoomId,

        @Schema(description = "대여 시작 시각 (대여 거래 전용)", example = "2026-05-01T10:00:00", nullable = true)
        LocalDateTime rentalStart,

        @Schema(description = "대여 종료 시각 (대여 거래 전용, start 이후)", example = "2026-05-03T10:00:00", nullable = true)
        LocalDateTime rentalEnd
) {
    public TransactionCreateCommand toCommand(Long requesterId) {
        return new TransactionCreateCommand(itemId, requesterId, chatRoomId, rentalStart, rentalEnd);
    }
}
