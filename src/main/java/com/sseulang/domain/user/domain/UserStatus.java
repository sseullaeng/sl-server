package com.sseulang.domain.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원 상태 (derived) — ACTIVE / SUSPENDED / WITHDRAWN / DORMANT / BLOCKED. "
        + "우선순위: WITHDRAWN > BLOCKED > SUSPENDED > DORMANT > ACTIVE.")
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    WITHDRAWN,
    DORMANT,
    BLOCKED
}
