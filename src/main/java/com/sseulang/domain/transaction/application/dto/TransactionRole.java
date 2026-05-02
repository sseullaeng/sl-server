package com.sseulang.domain.transaction.application.dto;

/**
 * 내 거래 목록 query 의 viewer 역할 필터.
 *
 * <ul>
 *   <li>{@link #BUYER} — viewer 가 buyer 인 거래만</li>
 *   <li>{@link #SELLER} — viewer 가 seller 인 거래만</li>
 *   <li>null — 양쪽 모두 (기본)</li>
 * </ul>
 *
 * <p>잘못된 query 값은 controller 에서 null 로 fallback (= 양쪽).</p>
 */
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
