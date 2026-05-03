package com.sseulang.domain.user.application.dto;

import com.sseulang.domain.user.domain.UserStatus;

import java.time.LocalDateTime;

/**
 * Admin 회원 검색 조건. null/blank 필드는 무시.
 *
 * <ul>
 *   <li>{@code keyword} — 닉네임 또는 이메일 LIKE 검색</li>
 *   <li>{@code status} — ACTIVE / SUSPENDED / WITHDRAWN / DORMANT (derive 기반)</li>
 *   <li>{@code createdAfter} / {@code createdBefore} — created_at 범위 (각 inclusive)</li>
 * </ul>
 */
public record AdminUserSearchCriteria(
        String keyword,
        UserStatus status,
        LocalDateTime createdAfter,
        LocalDateTime createdBefore
) {
    public static AdminUserSearchCriteria empty() {
        return new AdminUserSearchCriteria(null, null, null, null);
    }
}
