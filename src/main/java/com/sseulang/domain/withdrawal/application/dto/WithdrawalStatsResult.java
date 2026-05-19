package com.sseulang.domain.withdrawal.application.dto;

import com.sseulang.domain.withdrawal.domain.WithdrawalStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "관리자 dashboard — 출금 통계.")
public record WithdrawalStatsResult(
        @Schema(example = "87", description = "전체 출금 신청 수") long total,
        @Schema(description = "status 별 카운트 — 신청/승인/완료/거부",
                example = "{\"신청\":3,\"승인\":1,\"완료\":80,\"거부\":3}")
        Map<WithdrawalStatus, Long> byStatus,
        @Schema(example = "8000000", description = "완료된 출금 누적 금액 (KRW)") long completedAmount
) {}
