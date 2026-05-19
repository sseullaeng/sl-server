package com.sseulang.domain.transaction.domain.event;

public record TransactionReturnRequestedEvent(
        Long transactionId,
        Long buyerId,
        Long sellerId
) { }
