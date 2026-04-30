package com.sseulang.domain.transaction.domain;

/** 거래 상태별 집계 결과 — Repository GROUP BY 응답을 받기 위한 record. */
public record TransactionStatusCount(TransactionStatus status, long count) {}
