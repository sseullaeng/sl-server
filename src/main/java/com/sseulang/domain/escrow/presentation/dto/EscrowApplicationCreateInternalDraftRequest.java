package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowApplicationCreateInternalDraftCommand;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "내부 거래대행 draft 신청 — 판매자가 본인 영역만 입력. 구매자는 buyer-info PATCH 로 별도 입력.")
public record EscrowApplicationCreateInternalDraftRequest(
        @Schema(example = "7") @NotNull Long chatRoomId,
        @Schema(example = "123") @NotNull Long itemId,

        @Schema(example = "INTERNAL") @NotNull TradeMode tradeMode,
        @Schema(example = "both") @NotNull FeePayer feePayer,

        @Schema(example = "30000") @Min(0) long itemPrice,
        @Schema(example = "맥북 프로") @NotBlank @Size(max = 500) String itemDescription,

        @NotBlank @Size(max = 255) String pickupAddress,
        @NotNull BigDecimal pickupLat,
        @NotNull BigDecimal pickupLng,
        @Schema(description = "대여 시작 예정 시각. 대여 한정. chatRoom 에 buyer 의 사전 대여 신청(/rental-request) 이 있으면 백엔드가 그 값으로 자동 채움 — 누락 가능. 없으면 필수.")
        LocalDateTime rentalStartAt,
        @Schema(description = "대여 종료 예정 시각. 대여 한정. chatRoom 에 buyer 사전 신청 있으면 자동 채움 — 누락 가능.")
        LocalDateTime rentalEndAt,

        @NotNull Weight weight,
        @NotNull Volume volume,
        @NotNull Fragility fragility,
        @Size(max = 500) String deliveryNotes,

        @Size(max = 5) List<@Size(max = 500) String> imageUrls
) {
    public EscrowApplicationCreateInternalDraftCommand toCommand(Long requesterId) {
        return new EscrowApplicationCreateInternalDraftCommand(
                requesterId, chatRoomId, itemId,
                tradeMode, feePayer,
                itemPrice, itemDescription,
                pickupAddress, pickupLat, pickupLng,
                rentalStartAt, rentalEndAt,
                weight, volume, fragility, deliveryNotes,
                imageUrls
        );
    }
}
