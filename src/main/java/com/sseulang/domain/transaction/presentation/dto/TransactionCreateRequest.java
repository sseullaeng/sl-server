package com.sseulang.domain.transaction.presentation.dto;

import com.sseulang.domain.transaction.application.dto.TransactionCreateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

@Schema(description = "거래 생성 (구매자가 호출). 채팅중 상태로 시작. 대여 거래만 rentalStart/End 필요.")
public record TransactionCreateRequest(
        @Schema(description = "거래 대상 itemId", example = "42")
        @NotNull @Positive Long itemId,

        @Schema(description = "대여 시작 시각 (대여 거래 전용)", example = "2026-05-01T10:00:00", nullable = true)
        LocalDateTime rentalStart,

        @Schema(description = "대여 종료 시각 (대여 거래 전용, start 이후)", example = "2026-05-03T10:00:00", nullable = true)
        LocalDateTime rentalEnd
) {
    public TransactionCreateCommand toCommand(Long buyerId) {
        return new TransactionCreateCommand(itemId, buyerId, rentalStart, rentalEnd);
    }
}
