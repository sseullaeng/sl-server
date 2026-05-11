package com.sseulang.domain.escrow.application.dto;

import java.time.LocalDateTime;

public record EscrowPaymentPreviewResult(
        Long applicationId,
        long myShare,
        long myBalance,
        long deficit,
        boolean canPay,
        boolean alreadyPaid,
        LocalDateTime paymentDueAt
) { }
