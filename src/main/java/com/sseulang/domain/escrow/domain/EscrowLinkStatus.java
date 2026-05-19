package com.sseulang.domain.escrow.domain;

public enum EscrowLinkStatus {
    대기,
    완료,
    만료,
    취소;

    public boolean isTerminal() {
        return this != 대기;
    }
}
