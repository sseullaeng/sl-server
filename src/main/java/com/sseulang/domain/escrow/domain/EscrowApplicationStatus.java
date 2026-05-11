package com.sseulang.domain.escrow.domain;

/**
 * Escrow application 상태머신.
 *
 * <pre>
 *   [내부 흐름 — PR-B-4]
 *   정보입력대기 → (양쪽 입력 완료) → 결제대기 → 결제완료 → 진행중 → 완료
 *
 *   [외부 link 흐름]
 *   결제대기 → 결제완료 (양쪽 share 결제 — feePayer 별 분기) → 진행중 (라이더 자동 매칭)
 *   진행중   → 완료 (Mode B: buyer 수령 확인 + 정산 / Mode A: 배송완료 + 정산)
 *   any 시점 → 취소 (시점별 환불 정책 — 결정 #6)
 * </pre>
 */
public enum EscrowApplicationStatus {
    정보입력대기,
    결제대기,
    결제완료,
    진행중,
    완료,
    취소;

    public boolean isTerminal() {
        return this == 완료 || this == 취소;
    }

    public boolean isPaymentDone() {
        return this != 결제대기 && this != 정보입력대기;
    }

    /** 매칭 후 시점 — 취소 시 fee 100% 부담 정책 (결정 #6 갱신). */
    public boolean isAfterMatching() {
        return this == 진행중 || this == 완료;
    }
}
