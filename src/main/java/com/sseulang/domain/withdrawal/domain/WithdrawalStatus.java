package com.sseulang.domain.withdrawal.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "출금 상태. 신청→승인→완료 또는 거부(잔액 자동 환불).")
public enum WithdrawalStatus {
    신청,
    승인,
    거부,
    완료,
    취소;

    public boolean isTerminal() {
        return this == 거부 || this == 완료 || this == 취소;
    }

    
    public boolean canAdminProcess() {
        return this == 신청;
    }

    
    public boolean canComplete() {
        return this == 승인;
    }

    
    public boolean canUserCancel() {
        return this == 신청;
    }

    
    public boolean requiresRefundOnExit() {
        return this == 신청;
    }
}
