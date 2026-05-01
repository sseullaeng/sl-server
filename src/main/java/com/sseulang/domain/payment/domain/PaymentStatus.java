package com.sseulang.domain.payment.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 결제 상태. DB ENUM('대기','진행중','완료','실패','환불진행중','환불완료','환불실패') 1:1.
 * <p>상태 머신:</p>
 * <pre>
 *   대기 → 진행중 → 완료
 *                ↘ 실패
 *   완료 → 환불진행중 → 환불완료
 *                    ↘ 환불실패
 * </pre>
 */
@Schema(description = "결제 상태. 대기→진행중→완료/실패. 완료→환불진행중→환불완료/환불실패.")
public enum PaymentStatus {
    대기,
    진행중,
    완료,
    실패,
    환불진행중,
    환불완료,
    환불실패;

    public boolean isPaid() {
        return this == 완료;
    }

    public boolean canConfirm() {
        return this == 대기 || this == 진행중;
    }

    public boolean canRefund() {
        return this == 완료;
    }
}
