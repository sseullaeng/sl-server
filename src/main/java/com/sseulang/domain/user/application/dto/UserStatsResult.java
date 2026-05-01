package com.sseulang.domain.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 통계 — total / blocked / deleted 의 단순 카운트.
 * active = total − (blocked + deleted) 는 derived. 단, blocked && deleted 인 사용자가 있으면
 * active 계산에 음수가 나올 수 있어 service 에서 max(0, ...) 가드.
 */
@Schema(description = "관리자 dashboard — 회원 통계.")
public record UserStatsResult(
        @Schema(example = "1234", description = "전체 회원 수 (탈퇴/차단 포함)") long total,
        @Schema(example = "12", description = "차단된 회원 수") long blocked,
        @Schema(example = "8", description = "탈퇴(soft delete) 회원 수") long deleted,
        @Schema(example = "1214", description = "활성 회원 수 (차단/탈퇴 제외, 음수 가드)") long active
) {}
