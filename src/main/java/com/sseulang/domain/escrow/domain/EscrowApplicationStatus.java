package com.sseulang.domain.escrow.domain;

public enum EscrowApplicationStatus {
    정보입력대기,
    결제대기,
    결제완료,
    진행중,
    // 대여 한정 — confirmReceipt 후 buyer 가 사용 중인 상태. seller 정산은 confirmReturn 시점에 일어남.
    사용중,
    // 대여 한정 — buyer 가 [반납요청] 누른 후. return delivery 모집/배달 진행 중.
    반납중,
    완료,
    취소;

    public boolean isTerminal() {
        return this == 완료 || this == 취소;
    }

    public boolean isPaymentDone() {
        return this != 결제대기 && this != 정보입력대기;
    }


    public boolean isAfterMatching() {
        return this == 진행중 || this == 사용중 || this == 반납중 || this == 완료;
    }

    // 대여 한정 — confirmReceipt 가 settle 안 하고 사용중 진입할 수 있는 상태.
    public boolean canEnterUsing() {
        return this == 진행중;
    }

    // 대여 한정 — buyer [반납요청] 가능 status.
    public boolean canRequestReturn() {
        return this == 사용중;
    }

    // 대여 한정 — seller [회신확인] = 거래완료 가능 status. return delivery 완료 후.
    public boolean canConfirmReturn() {
        return this == 반납중;
    }
}
