package com.sseulang.domain.transaction.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 거래 상태. DB ENUM('채팅중','예약','인계완료','거래완료','취소') 1:1 매핑 (V16 라운드 11).
 * 가이드 §5.1: {@code 채팅중 → 예약 → 인계완료 → 거래완료} (또는 {@code 취소}).
 *
 * <p>라운드 11 흐름: 예약 시 buyer escrow hold, 인계확인(seller) → 인계완료, 인수확인(buyer) → 거래완료
 * 자동 전이 + 정산 (buyer hold 해제 + seller credit). 인계완료 이후 단순 취소 차단 — R2 분쟁 흐름.</p>
 */
@Schema(description = "거래 상태 머신. 채팅중→예약→인계완료→거래완료 (또는 취소).")
public enum TransactionStatus {
    채팅중,
    예약,
    인계완료,
    거래완료,
    취소;

    public boolean isTerminal() {
        return this == 거래완료 || this == 취소;
    }

    public boolean canReserve() {
        return this == 채팅중;
    }

    /** seller 인계확인 — 예약 상태에서만 가능. */
    public boolean canHandover() {
        return this == 예약;
    }

    /** buyer 인수확인 — 인계완료 상태에서만 가능 (자동으로 거래완료 전이 + 정산). */
    public boolean canReceive() {
        return this == 인계완료;
    }

    /**
     * 라운드 11 합의 (B-3): 채팅중 / 예약 단계만 단순 취소 가능. 인계완료 이후엔 차단 — R2 분쟁 endpoint 영역.
     */
    public boolean canCancel() {
        return this == 채팅중 || this == 예약;
    }
}
