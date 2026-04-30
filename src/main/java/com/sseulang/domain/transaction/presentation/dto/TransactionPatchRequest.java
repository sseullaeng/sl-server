package com.sseulang.domain.transaction.presentation.dto;

import com.sseulang.domain.transaction.domain.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 거래 상태 전이 요청. {@code action} 으로 의도된 상태(예약/거래완료/취소) 명시.
 * cancelReason 은 action=취소 일 때만 의미. 다른 action 일 때 null/무시.
 */
@Schema(description = "거래 상태 전이. action 별 권한: 예약/거래완료=seller, 취소=양쪽 참여자.")
public record TransactionPatchRequest(
        @Schema(description = "전이할 상태", example = "예약",
                allowableValues = {"예약", "거래완료", "취소"})
        @NotNull TransactionStatus action,

        @Schema(description = "취소 사유 (action=취소 전용)", example = "구매자 변심", nullable = true, maxLength = 255)
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
