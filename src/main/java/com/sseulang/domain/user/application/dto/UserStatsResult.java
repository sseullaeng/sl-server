package com.sseulang.domain.user.application.dto;

/**
 * 회원 통계 — total / blocked / deleted 의 단순 카운트.
 * active = total − (blocked + deleted) 는 derived. 단, blocked && deleted 인 사용자가 있으면
 * active 계산에 음수가 나올 수 있어 service 에서 max(0, ...) 가드.
 */
public record UserStatsResult(long total, long blocked, long deleted, long active) {}
