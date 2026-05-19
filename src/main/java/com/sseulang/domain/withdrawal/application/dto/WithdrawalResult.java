package com.sseulang.domain.withdrawal.application.dto;

import com.sseulang.domain.withdrawal.domain.Withdrawal;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;

import java.time.LocalDateTime;

public record WithdrawalResult(
        Long id,
        Long userId,
        long amount,
        String bankName,
        String accountNumber,
        String accountHolder,
        WithdrawalStatus status,
        Long adminId,
        String adminMemo,
        LocalDateTime requestedAt,
        LocalDateTime processedAt
) {
    public static WithdrawalResult from(Withdrawal w) {
        return new WithdrawalResult(
                w.getId(), w.getUserId(), w.getAmount(),
                w.getBankName(), w.getAccountNumber(), w.getAccountHolder(),
                w.getStatus(), w.getAdminId(), w.getAdminMemo(),
                w.getRequestedAt(), w.getProcessedAt()
        );
    }
}
