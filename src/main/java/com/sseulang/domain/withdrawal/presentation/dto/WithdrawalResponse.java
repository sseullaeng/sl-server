package com.sseulang.domain.withdrawal.presentation.dto;

import com.sseulang.domain.withdrawal.application.dto.WithdrawalResult;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;

import java.time.LocalDateTime;

public record WithdrawalResponse(
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
    public static WithdrawalResponse from(WithdrawalResult r) {
        return new WithdrawalResponse(
                r.id(), r.userId(), r.amount(),
                r.bankName(), r.accountNumber(), r.accountHolder(),
                r.status(), r.adminId(), r.adminMemo(),
                r.requestedAt(), r.processedAt()
        );
    }
}
