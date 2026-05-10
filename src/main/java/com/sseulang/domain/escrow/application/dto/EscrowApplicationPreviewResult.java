package com.sseulang.domain.escrow.application.dto;

import java.math.BigDecimal;

/**
 * 미리보기 응답. 실제 application 생성 X — 폼 작성 중 실시간 표시용.
 *
 * @param distanceKm     좌표 기반 산정 거리 (소수점 2)
 * @param deliveryFee    라이더 보상 (원)
 * @param commissionFee  플랫폼 수익 (Mode B 만 > 0)
 * @param totalFee       총 fee (deliveryFee + commissionFee + (INTERNAL ? itemPrice : 0))
 * @param buyerPayable   feePayer 별 buyer 부담분 (BOTH=50%, SELLER=0, BUYER=feeTotal)
 * @param sellerPayable  feePayer 별 seller 부담분
 * @param commissionRate snapshot — 정책 변경 시 실신청 시점에 재계산
 */
public record EscrowApplicationPreviewResult(
        BigDecimal distanceKm,
        long deliveryFee,
        long commissionFee,
        long totalFee,
        long buyerPayable,
        long sellerPayable,
        BigDecimal commissionRate
) {
}
