package com.sseulang.domain.transaction.application.dto;

import com.sseulang.domain.transaction.domain.TransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "관리자 dashboard — 거래 통계. byStatus 는 모든 status 포함 (0 건도 0L).")
public record TransactionStatsResult(
        @Schema(example = "568", description = "전체 거래 수") long total,
        @Schema(description = "status 별 카운트 — 채팅중/예약/거래완료/취소", example = "{\"채팅중\":120,\"예약\":35,\"거래완료\":380,\"취소\":33}")
        Map<TransactionStatus, Long> byStatus
) {}
