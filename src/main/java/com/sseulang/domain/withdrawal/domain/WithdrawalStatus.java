package com.sseulang.domain.withdrawal.domain;

/**
 * 출금 상태. V1 스키마 ENUM 1:1 매핑. 가이드 §4.8 출금 흐름.
 *
 * <pre>
 *   신청 → 승인 → 완료
 *     ├── 거부 (관리자)
 *     └── 취소 (사용자, 신청 상태만)
 * </pre>
 */
public enum WithdrawalStatus {
    신청,
    승인,
    거부,
    완료,
    취소;

    public boolean isTerminal() {
        return this == 거부 || this == 완료 || this == 취소;
    }

    /** 관리자가 처리할 수 있는 상태 (승인/거부의 진입). */
    public boolean canAdminProcess() {
        return this == 신청;
    }

    /** 외부 이체 완료 처리 가능한 상태 (승인 → 완료). */
    public boolean canComplete() {
        return this == 승인;
    }

    /** 사용자가 직접 취소 가능한 상태 (신청 상태만). */
    public boolean canUserCancel() {
        return this == 신청;
    }

    /** 잔액 원복이 필요한 상태 (거부/취소 진입 시 호출). */
    public boolean requiresRefundOnExit() {
        return this == 신청;
    }
}
