package com.sseulang.domain.payment.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "결제 수단 — CARD(카드) / TRANSFER(계좌이체) / VIRTUAL_ACCOUNT(가상계좌) / POINT(포인트). 토스 SDK 응답을 그대로 매핑.")
public enum PaymentMethod {
    CARD,
    TRANSFER,
    VIRTUAL_ACCOUNT,
    POINT
}
