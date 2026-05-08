package com.sseulang.domain.transaction.domain.event;

/**
 * 거래 취소 시점 — 양쪽 참여자 알림용 (라운드 11). canceledByUserId 는 취소를 누른 사용자 (buyer 또는 seller).
 */
public record TransactionCanceledEvent(
        Long transactionId,
        Long buyerId,
        Long sellerId,
        Long canceledByUserId
) { }
