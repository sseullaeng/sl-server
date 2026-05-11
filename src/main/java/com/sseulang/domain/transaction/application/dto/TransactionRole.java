package com.sseulang.domain.transaction.application.dto;

public enum TransactionRole {
    BUYER,
    SELLER;

    public static TransactionRole parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return TransactionRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
