package com.sseulang.domain.payment.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 결제 종류. DB ENUM('충전','대여금','보증금','수수료','환불') 1:1.
 * 본 PR(Day 7) 은 {@link #충전} 에 집중. 거래 결제(대여금/보증금)는 Day 8 영역.
 */
@Schema(description = "결제 종류 — 충전(잔액 충전) / 대여금 / 보증금 / 수수료 / 환불.")
public enum PaymentType {
    충전,
    대여금,
    보증금,
    수수료,
    환불
}
