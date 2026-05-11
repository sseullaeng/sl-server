package com.sseulang.domain.escrow.domain;

public enum EscrowApplicationStatus {
    정보입력대기,
    결제대기,
    결제완료,
    진행중,
    완료,
    취소;

    public boolean isTerminal() {
        return this == 완료 || this == 취소;
    }

    public boolean isPaymentDone() {
        return this != 결제대기 && this != 정보입력대기;
    }

    
    public boolean isAfterMatching() {
        return this == 진행중 || this == 완료;
    }
}
