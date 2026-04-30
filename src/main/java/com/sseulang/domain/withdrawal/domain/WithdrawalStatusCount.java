package com.sseulang.domain.withdrawal.domain;

/** 출금 상태별 집계 결과. */
public record WithdrawalStatusCount(WithdrawalStatus status, long count) {}
