package com.sseulang.domain.transaction.presentation.dto;

import com.sseulang.domain.transaction.domain.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 거래 상태 전이 요청. {@code action} 으로 의도된 상태(예약/거래완료/취소) 명시.
 * cancelReason 은 action=취소 일 때만 의미. 다른 action 일 때 null/무시.
 */
public record TransactionPatchRequest(
        @NotNull TransactionStatus action,
        @Size(max = 255) String cancelReason
) {
    public boolean isReserve() {
        return action == TransactionStatus.예약;
    }

    public boolean isComplete() {
        return action == TransactionStatus.거래완료;
    }

    public boolean isCancel() {
        return action == TransactionStatus.취소;
    }
}
