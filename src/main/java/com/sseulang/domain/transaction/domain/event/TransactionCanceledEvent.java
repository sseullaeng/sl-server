package com.sseulang.domain.transaction.domain.event;

public record TransactionCanceledEvent(
        Long transactionId,
        Long buyerId,
        Long sellerId,
        Long canceledByUserId
) { }
