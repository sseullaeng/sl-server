package com.sseulang.domain.withdrawal.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 출금 처리 요청 — action: APPROVE / REJECT / COMPLETE.
 */
public record AdminWithdrawalDecisionRequest(
        @NotNull(message = "action 은 필수입니다")
        Action action,

        @Size(max = 500)
        String memo
) {
    public enum Action {
        APPROVE,
        REJECT,
        COMPLETE
    }
}
