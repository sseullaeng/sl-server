package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowApplicationPreviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "거래대행 수수료 미리보기 응답.")
public record EscrowApplicationPreviewResponse(
        @Schema(example = "5.42") BigDecimal distanceKm,
        @Schema(example = "4000") long deliveryFee,
        @Schema(example = "1000") long commissionFee,
        @Schema(example = "5000", description = "deliveryFee + commissionFee + (INTERNAL ? itemPrice : 0)")
        long totalFee,
        @Schema(example = "2500", description = "feePayer 별 구매자 부담분")
        long buyerPayable,
        @Schema(example = "2500", description = "feePayer 별 판매자 부담분")
        long sellerPayable,
        @Schema(example = "0.05") BigDecimal commissionRate
) {
    public static EscrowApplicationPreviewResponse from(EscrowApplicationPreviewResult r) {
        return new EscrowApplicationPreviewResponse(
                r.distanceKm(), r.deliveryFee(), r.commissionFee(), r.totalFee(),
                r.buyerPayable(), r.sellerPayable(), r.commissionRate()
        );
    }
}
