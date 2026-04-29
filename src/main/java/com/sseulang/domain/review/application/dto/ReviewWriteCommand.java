package com.sseulang.domain.review.application.dto;

public record ReviewWriteCommand(
        Long transactionId,
        Long reviewerId,
        int rating,
        String comment
) { }
