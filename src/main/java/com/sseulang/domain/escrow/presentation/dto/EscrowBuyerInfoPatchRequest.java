package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowBuyerInfoPatchCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "구매자 영역 입력 — 정보입력대기 상태에서만. 수령지 + 수령자 연락처. "
        + "양쪽 입력 완료 시 자동으로 fee 산정 + 결제대기 전환.")
public record EscrowBuyerInfoPatchRequest(
        @NotBlank @Size(max = 255) String deliveryAddress,
        @NotNull BigDecimal deliveryLat,
        @NotNull BigDecimal deliveryLng,

        @NotBlank @Pattern(regexp = "^[0-9\\-+]+$", message = "전화번호 형식이 올바르지 않습니다")
        @Size(max = 20)
        String receiverPhone
) {
    public EscrowBuyerInfoPatchCommand toCommand() {
        return new EscrowBuyerInfoPatchCommand(
                deliveryAddress, deliveryLat, deliveryLng, receiverPhone
        );
    }
}
