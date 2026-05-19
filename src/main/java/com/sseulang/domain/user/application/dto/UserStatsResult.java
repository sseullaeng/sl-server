package com.sseulang.domain.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 dashboard — 회원 통계.")
public record UserStatsResult(
        @Schema(example = "1234", description = "전체 회원 수 (탈퇴/차단 포함)") long total,
        @Schema(example = "12", description = "차단된 회원 수") long blocked,
        @Schema(example = "8", description = "탈퇴(soft delete) 회원 수") long deleted,
        @Schema(example = "1214", description = "활성 회원 수 (차단/탈퇴 제외, 음수 가드)") long active
) {}
