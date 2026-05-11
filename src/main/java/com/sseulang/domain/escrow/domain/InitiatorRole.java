package com.sseulang.domain.escrow.domain;

public enum InitiatorRole {
    buyer,
    seller;

    public InitiatorRole opposite() {
        return this == buyer ? seller : buyer;
    }
}
