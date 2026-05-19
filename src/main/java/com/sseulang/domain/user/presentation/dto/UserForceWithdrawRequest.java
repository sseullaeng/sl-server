package com.sseulang.domain.user.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 강제 탈퇴 요청. reason 은 감사 로그용 (DB 미저장).")
public record UserForceWithdrawRequest(
        @Schema(example = "다중 신고 누적 + 관리자 검토", nullable = true, maxLength = 500)
        @Size(max = 500) String reason
) { }
