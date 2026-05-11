package com.sseulang.domain.withdrawal.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 출금 처리. APPROVE(신청→승인) / REJECT(신청→거부, 잔액 환불) / COMPLETE(승인→완료, 외부 이체).")
public record AdminWithdrawalDecisionRequest(
        @Schema(description = "처리 액션", example = "APPROVE", allowableValues = {"APPROVE", "REJECT", "COMPLETE"})
        @NotNull(message = "action 은 필수입니다")
        Action action,

        @Schema(description = "처리 메모 (선택)", example = "본인 확인 완료", maxLength = 500, nullable = true)
        @Size(max = 500)
        String memo
) {
    public enum Action {
        APPROVE,
        REJECT,
        COMPLETE
    }
}
