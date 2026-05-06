package com.sseulang.domain.escrow.domain;

/** 신청자 역할 (수신자는 반대 role 자동). 가이드 §5.7 거래대행. */
public enum InitiatorRole {
    buyer,
    seller;

    public InitiatorRole opposite() {
        return this == buyer ? seller : buyer;
    }
}
