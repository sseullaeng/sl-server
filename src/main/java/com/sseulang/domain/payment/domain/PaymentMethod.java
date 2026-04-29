package com.sseulang.domain.payment.domain;

/**
 * 결제 수단. 토스가 응답에 String 으로 반환 (CARD / TRANSFER / VIRTUAL_ACCOUNT 등).
 * DB는 VARCHAR(30) — Java enum 으로는 그대로 영문 식별자 사용.
 */
public enum PaymentMethod {
    CARD,
    TRANSFER,
    VIRTUAL_ACCOUNT,
    POINT
}
