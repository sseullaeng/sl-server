package com.sseulang.domain.transaction.application.dto;

import java.time.LocalDateTime;

public record ReviewableTransactionResult(
        Long transactionId,
        Long reviewerId,
        Long revieweeId,
        LocalDateTime completedAt
) { }
