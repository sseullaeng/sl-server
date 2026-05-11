package com.sseulang.domain.transaction.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 상태 머신. 채팅중→예약→인계완료→거래완료 (또는 취소).")
public enum TransactionStatus {
    채팅중,
    예약,
    인계완료,
    거래완료,
    취소;

    public boolean isTerminal() {
        return this == 거래완료 || this == 취소;
    }

    public boolean canReserve() {
        return this == 채팅중;
    }

    
    public boolean canHandover() {
        return this == 예약;
    }

    
    public boolean canReceive() {
        return this == 인계완료;
    }

    

    public boolean canCancel() {
        return this == 채팅중 || this == 예약;
    }
}
