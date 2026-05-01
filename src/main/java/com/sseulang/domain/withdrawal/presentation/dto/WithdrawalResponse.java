package com.sseulang.domain.withdrawal.presentation.dto;

import com.sseulang.domain.withdrawal.application.dto.WithdrawalResult;
import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "출금 신청 — 신청→승인→완료 / 거부. 신청 즉시 잔액 차감, 거부 시 자동 환불.")
public record WithdrawalResponse(
        @Schema(example = "31") Long id,
        @Schema(example = "100") Long userId,
        @Schema(example = "100000") long amount,
        @Schema(example = "신한") String bankName,
        @Schema(example = "110-123-456789") String accountNumber,
        @Schema(example = "홍길동") String accountHolder,
        WithdrawalStatus status,
        @Schema(description = "처리한 관리자 id (신청 단계는 null)") Long adminId,
        @Schema(description = "거부 사유 등 관리자 메모", example = "본인 계좌 미일치") String adminMemo,
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
