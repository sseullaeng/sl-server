package com.sseulang.domain.item.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "대여 단위 — 시간 / 일 / 주 / 월. 대여 거래에만 사용 (판매/나눔은 null).")
public enum RentalUnit {
    시간,
    일,
    주,
    월
}
