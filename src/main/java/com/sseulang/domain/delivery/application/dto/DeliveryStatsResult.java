package com.sseulang.domain.delivery.application.dto;

import com.sseulang.domain.delivery.domain.DeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "관리자 dashboard — 배달대행 통계.")
public record DeliveryStatsResult(
        @Schema(example = "42", description = "전체 배달 요청 수") long total,
        @Schema(description = "status 별 카운트 — 모집중/수락/배송중/배송완료/정산완료/취소",
                example = "{\"모집중\":3,\"수락\":2,\"배송중\":1,\"배송완료\":1,\"정산완료\":33,\"취소\":2}")
        Map<DeliveryStatus, Long> byStatus,
        @Schema(example = "165000", description = "정산완료 상태 fee 합계 — 라이더 수익 총액 추정 (KRW)") long settledFeeTotal
) {}
