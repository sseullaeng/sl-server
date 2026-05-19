package com.sseulang.domain.payment.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "결제 종류 — 충전(잔액 충전) / 대여금 / 보증금 / 수수료 / 환불.")
public enum PaymentType {
    충전,
    대여금,
    보증금,
    수수료,
    환불
}
