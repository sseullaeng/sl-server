package com.sseulang.domain.transaction.domain.event;

/**
 * seller 인계확인 시점 — buyer 에게 인수확인 부탁 알림용 (라운드 11).
 */
public record TransactionHandoverConfirmedEvent(
        Long transactionId,
        Long buyerId
) { }
