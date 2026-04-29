package com.sseulang.domain.item.domain;

/**
 * 거래 타입. DB ENUM('대여','판매','나눔') 과 1:1 매핑 — {@code @Enumerated(EnumType.STRING)} 으로 name 그대로 저장.
 */
public enum TradeType {
    대여,
    판매,
    나눔;

    public boolean requiresDeposit() {
        return this == 대여;
    }
}
