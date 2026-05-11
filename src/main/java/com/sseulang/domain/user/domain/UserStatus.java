package com.sseulang.domain.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 상태 (derived) — ACTIVE / SUSPENDED / WITHDRAWN / DORMANT")
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    WITHDRAWN,
    DORMANT
}
