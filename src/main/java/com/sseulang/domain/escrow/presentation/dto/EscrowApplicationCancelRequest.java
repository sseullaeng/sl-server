package com.sseulang.domain.escrow.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "거래대행 신청 취소. 매칭 전엔 양쪽 환불, 매칭 후엔 결정 #6 정책.")
public record EscrowApplicationCancelRequest(
        @Schema(description = "취소 사유 (선택)", example = "단순 변심")
        @Size(max = 500) String reason
) {
}
