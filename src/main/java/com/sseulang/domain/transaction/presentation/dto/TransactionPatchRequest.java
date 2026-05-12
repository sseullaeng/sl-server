package com.sseulang.domain.transaction.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "거래 상태 전이. action 별 권한: 예약/인계확인=seller, 인수확인=buyer, 취소=양쪽 참여자.")
public record TransactionPatchRequest(
        @Schema(description = "전이 액션", example = "예약",
                allowableValues = {"예약", "인계확인", "인수확인", "취소"})
        @NotNull TransactionPatchAction action,

        @Schema(description = "취소 사유 (action=취소 전용)", example = "구매자 변심", nullable = true, maxLength = 255)
        @Size(max = 255) String cancelReason
) {
    public boolean isReserve() {
        return action == TransactionPatchAction.예약;
    }

    public boolean isHandover() {
        return action == TransactionPatchAction.인계확인;
    }

    public boolean isReceive() {
        return action == TransactionPatchAction.인수확인;
    }

    public boolean isComplete() {
        return action == TransactionPatchAction.완료;
    }

    public boolean isCancel() {
        return action == TransactionPatchAction.취소;
    }
}
