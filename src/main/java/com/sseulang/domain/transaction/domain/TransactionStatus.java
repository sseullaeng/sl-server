package com.sseulang.domain.transaction.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 상태 머신. 채팅중→예약→인계완료→거래완료 (또는 취소). "
        + "대여 한정 — 인계완료→반납요청→거래완료 또는 7일 자동 거래완료.")
public enum TransactionStatus {
    채팅중,
    예약,
    인계완료,
    반납요청,
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

    // 대여 한정 — 인계완료 상태에서 buyer(빌린 사람) 가 반납 요청.
    public boolean canRequestReturn() {
        return this == 인계완료;
    }

    // 대여 한정 — 반납요청 상태에서 seller(빌려준 사람) 가 회신 (=거래완료).
    public boolean canConfirmReturn() {
        return this == 반납요청;
    }


    public boolean canCancel() {
        return this == 채팅중 || this == 예약;
    }
}
