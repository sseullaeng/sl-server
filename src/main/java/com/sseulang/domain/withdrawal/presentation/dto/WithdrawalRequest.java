package com.sseulang.domain.withdrawal.presentation.dto;

import com.sseulang.domain.withdrawal.application.dto.WithdrawalRequestCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawalRequest(
        /**
         * 클라이언트 발급 멱등성 키 (UUID 권장). 같은 (사용자, 키) 재요청 시 새 출금 행 생성하지 않고
         * 기존 행을 그대로 반환 — 더블클릭/네트워크 재시도로 잔액 중복 차감 방지 (게이트 1).
         */
        @NotBlank(message = "idempotencyKey 는 필수입니다")
        @Size(max = 64)
        String idempotencyKey,

        @Min(value = 1, message = "amount 는 1 이상이어야 합니다")
        long amount,

        @NotBlank(message = "bankName 은 필수입니다")
        @Size(max = 50)
        String bankName,

        @NotBlank(message = "accountNumber 는 필수입니다")
        @Size(max = 50)
        String accountNumber,

        @NotBlank(message = "accountHolder 는 필수입니다")
        @Size(max = 50)
        String accountHolder
) {
    public WithdrawalRequestCommand toCommand(Long userId) {
        return new WithdrawalRequestCommand(userId, idempotencyKey, amount, bankName, accountNumber, accountHolder);
    }
}
