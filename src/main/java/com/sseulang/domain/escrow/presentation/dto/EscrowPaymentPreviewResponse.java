package com.sseulang.domain.escrow.presentation.dto;

import com.sseulang.domain.escrow.application.dto.EscrowPaymentPreviewResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "거래대행 본인 share 결제 미리보기 — 부족분/잔액/결제 가능 여부.")
public record EscrowPaymentPreviewResponse(
        @Schema(example = "42") Long applicationId,
        @Schema(example = "15000", description = "본인 분담 금액") long myShare,
        @Schema(example = "9000", description = "현재 포인트 잔액") long myBalance,
        @Schema(example = "6000", description = "부족분 = max(0, myShare - myBalance). 0 이면 즉시 결제 가능")
        long deficit,
        @Schema(example = "false", description = "즉시 결제 가능 여부 — 상태/시점/잔액/이미 결제 여부 모두 통과") boolean canPay,
        @Schema(example = "false", description = "이미 본인 share 결제 완료") boolean alreadyPaid,
        @Schema(description = "결제 마감 시각 — null 이면 마감 없음") LocalDateTime paymentDueAt
) {
    public static EscrowPaymentPreviewResponse from(EscrowPaymentPreviewResult r) {
        return new EscrowPaymentPreviewResponse(
                r.applicationId(), r.myShare(), r.myBalance(), r.deficit(),
                r.canPay(), r.alreadyPaid(), r.paymentDueAt()
        );
    }
}
