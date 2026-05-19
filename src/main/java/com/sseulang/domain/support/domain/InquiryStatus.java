package com.sseulang.domain.support.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "1:1 문의 처리 상태 — PENDING(대기) / PROCESSING(처리중) / DONE(완료)")
public enum InquiryStatus {
    PENDING,
    PROCESSING,
    DONE
}
