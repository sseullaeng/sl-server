package com.sseulang.domain.user.application.dto;

import com.sseulang.domain.user.domain.UserStatus;

import java.time.LocalDateTime;

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
