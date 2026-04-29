package com.sseulang.domain.transaction.domain;

/**
 * 거래 상태. DB ENUM('채팅중','예약','거래완료','취소') 1:1 매핑.
 * 가이드 §5.1: {@code 채팅중 → 예약 → 거래완료} (또는 {@code 취소}).
 */
public enum TransactionStatus {
    채팅중,
    예약,
    거래완료,
    취소;

    public boolean isTerminal() {
        return this == 거래완료 || this == 취소;
    }

    public boolean canReserve() {
        return this == 채팅중;
    }

    public boolean canComplete() {
        return this == 예약;
    }

    public boolean canCancel() {
        return this == 채팅중 || this == 예약;
    }
}
