package com.sseulang.domain.withdrawal.application.dto;

public record WithdrawalRequestCommand(
        Long userId,
        String idempotencyKey,
        long amount,
        String bankName,
        String accountNumber,
        String accountHolder
) {
    public WithdrawalRequestCommand {
        idempotencyKey = normalize(idempotencyKey, "idempotencyKey", 64);
        bankName = normalize(bankName, "bankName", 50);
        accountNumber = normalize(accountNumber, "accountNumber", 50);
        accountHolder = normalize(accountHolder, "accountHolder", 50);
    }

    private static String normalize(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 는 필수입니다");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(name + " 는 " + maxLength + "자 이하여야 합니다");
        }
        return trimmed;
    }
}
