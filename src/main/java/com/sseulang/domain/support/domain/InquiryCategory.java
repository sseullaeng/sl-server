package com.sseulang.domain.support.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "고객지원 카테고리 — 계정/거래/결제/배송/기타")
public enum InquiryCategory {
    계정,
    거래,
    결제,
    배송,
    기타
}
