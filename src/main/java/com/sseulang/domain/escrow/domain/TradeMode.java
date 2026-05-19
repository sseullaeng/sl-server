package com.sseulang.domain.escrow.domain;

public enum TradeMode {
    INTERNAL,
    EXTERNAL;

    public boolean isInternal() {
        return this == INTERNAL;
    }
}
