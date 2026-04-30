package com.sseulang.domain.withdrawal.presentation.dto;

import com.sseulang.domain.withdrawal.application.dto.WithdrawalRequestCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "포인트 출금 신청. 신청 시점에 즉시 잔액 차감 + 관리자 승인 후 외부 이체.")
public record WithdrawalRequest(
        /**
         * 클라이언트 발급 멱등성 키 (UUID 권장). 같은 (사용자, 키) 재요청 시 새 출금 행 생성하지 않고
         * 기존 행을 그대로 반환 — 더블클릭/네트워크 재시도로 잔액 중복 차감 방지 (게이트 1).
         */
        @Schema(description = "클라이언트 발급 멱등성 키 (UUID 권장)", example = "f47ac10b-58cc-4372-a567-0e02b2c3d479", maxLength = 64)
        @NotBlank(message = "idempotencyKey 는 필수입니다")
        @Size(max = 64)
        String idempotencyKey,

        @Schema(description = "출금 금액", example = "30000")
        @Min(value = 1, message = "amount 는 1 이상이어야 합니다")
        long amount,

        @Schema(description = "은행명", example = "신한", maxLength = 50)
        @NotBlank(message = "bankName 은 필수입니다")
        @Size(max = 50)
        String bankName,

        @Schema(description = "계좌번호", example = "110-123-456789", maxLength = 50)
        @NotBlank(message = "accountNumber 는 필수입니다")
        @Size(max = 50)
        String accountNumber,

        @Schema(description = "예금주", example = "홍길동", maxLength = 50)
        @NotBlank(message = "accountHolder 는 필수입니다")
        @Size(max = 50)
        String accountHolder
) {
    public WithdrawalRequestCommand toCommand(Long userId) {
        return new WithdrawalRequestCommand(userId, idempotencyKey, amount, bankName, accountNumber, accountHolder);
    }
}
