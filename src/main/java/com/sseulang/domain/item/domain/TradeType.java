package com.sseulang.domain.item.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "거래 종류 — 판매 / 대여 / 나눔.")
public enum TradeType {
    대여,
    판매,
    나눔;

    public boolean requiresDeposit() {
        return this == 대여;
    }
}
