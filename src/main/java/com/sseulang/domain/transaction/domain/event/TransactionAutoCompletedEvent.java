package com.sseulang.domain.transaction.domain.event;

public record TransactionAutoCompletedEvent(
        Long transactionId,
        Long buyerId,
        Long sellerId
) { }
