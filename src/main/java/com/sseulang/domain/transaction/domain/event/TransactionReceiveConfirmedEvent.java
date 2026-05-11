package com.sseulang.domain.transaction.domain.event;

public record TransactionReceiveConfirmedEvent(
        Long transactionId,
        Long sellerId,
        long settleAmount
) { }
