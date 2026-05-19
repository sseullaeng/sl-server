package com.sseulang.domain.transaction.domain.event;

public record TransactionHandoverConfirmedEvent(
        Long transactionId,
        Long buyerId
) { }
