package com.sseulang.domain.withdrawal.application.dto;

/**
 * 출금 신청 Command. 모든 문자열 필드(idempotencyKey + 계좌 정보)는 compact constructor 에서
 * 한 번만 정규화 (trim) — Withdrawal 저장 / 멱등성 비교 / 복구 조회 모두 동일 값을 보고
 * trim 위치 불일치로 dedup 이 거짓 거부되는 회귀 차단 (게이트 2 round 2).
 */
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
