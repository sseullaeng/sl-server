package com.sseulang.domain.transaction.domain.event;

/**
 * 거래 예약 시점 — buyer 알림용 (라운드 11). reserve 트랜잭션 commit 후 listener 가 처리.
 */
public record TransactionReservedEvent(
        Long transactionId,
        Long buyerId,
        Long sellerId,
        long price
) { }
