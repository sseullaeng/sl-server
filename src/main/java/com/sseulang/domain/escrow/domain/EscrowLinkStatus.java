package com.sseulang.domain.escrow.domain;

/**
 * Escrow link 상태머신.
 *
 * <pre>
 *   대기 → 완료 (수신자 확정 + application 생성됨)
 *   대기 → 만료 (expiresAt 경과)
 *   대기 → 취소 (신청자 직접 취소)
 * </pre>
 */
public enum EscrowLinkStatus {
    대기,
    완료,
    만료,
    취소;

    public boolean isTerminal() {
        return this != 대기;
    }
}
