package com.sseulang.domain.payment.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 결제 수단. 토스가 응답에 String 으로 반환 (CARD / TRANSFER / VIRTUAL_ACCOUNT 등).
 * DB는 VARCHAR(30) — Java enum 으로는 그대로 영문 식별자 사용.
 */
@Schema(description = "결제 수단 — CARD(카드) / TRANSFER(계좌이체) / VIRTUAL_ACCOUNT(가상계좌) / POINT(포인트). 토스 SDK 응답을 그대로 매핑.")
public enum PaymentMethod {
    CARD,
    TRANSFER,
    VIRTUAL_ACCOUNT,
    POINT
}
