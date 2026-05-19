package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowLinkCreateCommand;
import com.sseulang.domain.escrow.domain.FeePayer;
import com.sseulang.domain.escrow.domain.Fragility;
import com.sseulang.domain.escrow.domain.InitiatorRole;
import com.sseulang.domain.escrow.domain.TradeMode;
import com.sseulang.domain.escrow.domain.Volume;
import com.sseulang.domain.escrow.domain.Weight;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "거래대행 link 생성 (신청자). role + feePayer + tradeMode 결정. "
        + "라운드 12 분리 입력 흐름: role=seller 면 pickup/물품 영역, role=buyer 면 delivery/연락처 영역도 함께 전달.")
public record EscrowLinkCreateRequest(
        @Schema(description = "신청자 역할 (수신자는 반대)", example = "buyer", allowableValues = {"buyer", "seller"})
        @NotNull InitiatorRole role,

        @Schema(description = "수수료 부담", example = "buyer", allowableValues = {"buyer", "seller", "both"})
        @NotNull FeePayer feePayer,

        @Schema(description = "거래 모드 — INTERNAL (쓸랭 내) / EXTERNAL (외부 거래 + 배달만)",
                example = "INTERNAL", allowableValues = {"INTERNAL", "EXTERNAL"})
        @NotNull TradeMode tradeMode,

        // role=seller 일 때 — pickup + 물품 정보
        @Size(max = 255) String initiatorPickupAddress,
        @DecimalMin("33") @DecimalMax("39") BigDecimal initiatorPickupLat,
        @DecimalMin("124") @DecimalMax("132") BigDecimal initiatorPickupLng,
        @PositiveOrZero Long initiatorItemPrice,
        @Size(max = 500) String initiatorItemDescription,
        Weight initiatorWeight,
        Volume initiatorVolume,
        Fragility initiatorFragility,
        @Size(max = 500) String initiatorDeliveryNotes,
        List<@Size(max = 500) String> initiatorImageUrls,

        // role=buyer 일 때 — delivery + 수령인 연락처
        @Size(max = 255) String initiatorDeliveryAddress,
        @DecimalMin("33") @DecimalMax("39") BigDecimal initiatorDeliveryLat,
        @DecimalMin("124") @DecimalMax("132") BigDecimal initiatorDeliveryLng,
        @Size(max = 20) String initiatorReceiverPhone
) {
    public EscrowLinkCreateCommand toCommand(Long initiatorId) {
        return new EscrowLinkCreateCommand(
                initiatorId, role, feePayer, tradeMode,
                initiatorPickupAddress, initiatorPickupLat, initiatorPickupLng,
                initiatorItemPrice, initiatorItemDescription,
                initiatorWeight, initiatorVolume, initiatorFragility,
                initiatorDeliveryNotes, initiatorImageUrls,
                initiatorDeliveryAddress, initiatorDeliveryLat, initiatorDeliveryLng,
                initiatorReceiverPhone
        );
    }
}
