package com.sseulang.domain.transaction.domain.event;

public record TransactionReservedEvent(
        Long transactionId,
        Long buyerId,
        Long sellerId,
        long price
) { }
