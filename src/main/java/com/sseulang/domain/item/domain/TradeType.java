package com.sseulang.domain.item.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 거래 타입. DB ENUM('대여','판매','나눔') 과 1:1 매핑 — {@code @Enumerated(EnumType.STRING)} 으로 name 그대로 저장.
 */
@Schema(description = "거래 종류 — 판매 / 대여 / 나눔.")
public enum TradeType {
    대여,
    판매,
    나눔;

    public boolean requiresDeposit() {
        return this == 대여;
    }
}
