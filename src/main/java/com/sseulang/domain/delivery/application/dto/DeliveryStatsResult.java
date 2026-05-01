package com.sseulang.domain.delivery.application.dto;

import com.sseulang.domain.delivery.domain.DeliveryStatus;

import java.util.Map;

/**
 * 관리자 dashboard 용 배달대행 통계.
 *
 * @param total 전체 요청 건수
 * @param byStatus status 별 건수 (모든 enum 포함, 0 이면 0L)
 * @param settledFeeTotal 정산완료 상태 fee 합계 (KRW). 라이더 수익 총액 추정.
 */
public record DeliveryStatsResult(
        long total,
        Map<DeliveryStatus, Long> byStatus,
        long settledFeeTotal
) {}
