package com.sseulang.domain.withdrawal.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalTest {

    private static final Long USER = 1L;
    private static final Long ADMIN = 99L;
    private static final String IDEM = "idem-key-1";
    private static final long AMOUNT = 50_000L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 30, 12, 0);

    private Withdrawal newWithdrawal() {
        return Withdrawal.request(USER, IDEM, AMOUNT, "신한", "110-123-456789", "홍길동", NOW);
    }

    @Test
    @DisplayName("request 정상_status=신청, idempotencyKey/requestedAt 설정")
    void request_정상() {
        Withdrawal w = newWithdrawal();

        assertThat(w.getUserId()).isEqualTo(USER);
        assertThat(w.getIdempotencyKey()).isEqualTo(IDEM);
        assertThat(w.getAmount()).isEqualTo(AMOUNT);
        assertThat(w.getStatus()).isEqualTo(WithdrawalStatus.신청);
        assertThat(w.getRequestedAt()).isEqualTo(NOW);
        assertThat(w.getProcessedAt()).isNull();
        assertThat(w.getAdminId()).isNull();
    }

    @Test
    @DisplayName("request invalid 인자 거부 (idempotencyKey 누락 포함)")
    void request_invalid() {
        assertThatThrownBy(() -> Withdrawal.request(null, IDEM, AMOUNT, "a", "b", "c", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Withdrawal.request(USER, IDEM, 0L, "a", "b", "c", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Withdrawal.request(USER, "", AMOUNT, "a", "b", "c", NOW))
                .as("idempotencyKey 빈값 거부")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Withdrawal.request(USER, null, AMOUNT, "a", "b", "c", NOW))
                .as("idempotencyKey null 거부")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Withdrawal.request(USER, IDEM, AMOUNT, "", "b", "c", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Withdrawal.request(USER, IDEM, AMOUNT, "a", null, "c", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Withdrawal.request(USER, IDEM, AMOUNT, "a", "b", "", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cancel 신청 상태_status=취소")
    void cancel_정상() {
        Withdrawal w = newWithdrawal();
        w.cancel(NOW.plusMinutes(5));

        assertThat(w.getStatus()).isEqualTo(WithdrawalStatus.취소);
        assertThat(w.getProcessedAt()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    @DisplayName("cancel 승인 상태_WITHDRAWAL_NOT_CANCELABLE")
    void cancel_승인상태_거부() {
        Withdrawal w = newWithdrawal();
        w.approve(ADMIN, "ok", NOW.plusMinutes(1));
        assertThatThrownBy(() -> w.cancel(NOW.plusMinutes(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_NOT_CANCELABLE);
    }

    @Test
    @DisplayName("approve 신청 → 승인_adminId/memo 기록")
    void approve_정상() {
        Withdrawal w = newWithdrawal();
        w.approve(ADMIN, "확인 완료", NOW.plusMinutes(1));

        assertThat(w.getStatus()).isEqualTo(WithdrawalStatus.승인);
        assertThat(w.getAdminId()).isEqualTo(ADMIN);
        assertThat(w.getAdminMemo()).isEqualTo("확인 완료");
    }

    @Test
    @DisplayName("approve 신청 외 상태_WITHDRAWAL_INVALID_STATE")
    void approve_상태_거부() {
        Withdrawal w = newWithdrawal();
        w.approve(ADMIN, null, NOW.plusMinutes(1));
        assertThatThrownBy(() -> w.approve(ADMIN, null, NOW.plusMinutes(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_INVALID_STATE);
    }

    @Test
    @DisplayName("reject 신청 → 거부_상태 기록")
    void reject_정상() {
        Withdrawal w = newWithdrawal();
        w.reject(ADMIN, "계좌 오류", NOW.plusMinutes(1));

        assertThat(w.getStatus()).isEqualTo(WithdrawalStatus.거부);
        assertThat(w.getAdminId()).isEqualTo(ADMIN);
        assertThat(w.getAdminMemo()).isEqualTo("계좌 오류");
    }

    @Test
    @DisplayName("markAsCompleted 승인 → 완료")
    void markAsCompleted_정상() {
        Withdrawal w = newWithdrawal();
        w.approve(ADMIN, null, NOW.plusMinutes(1));
        w.markAsCompleted(NOW.plusMinutes(10));

        assertThat(w.getStatus()).isEqualTo(WithdrawalStatus.완료);
        assertThat(w.getProcessedAt()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    @DisplayName("markAsCompleted 승인 외 상태_WITHDRAWAL_INVALID_STATE")
    void markAsCompleted_상태_거부() {
        Withdrawal w = newWithdrawal();
        // 신청 상태에서 바로 complete 불가
        assertThatThrownBy(() -> w.markAsCompleted(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WITHDRAWAL_INVALID_STATE);
    }

    @Test
    @DisplayName("isOwnedBy")
    void isOwnedBy() {
        Withdrawal w = newWithdrawal();
        assertThat(w.isOwnedBy(USER)).isTrue();
        assertThat(w.isOwnedBy(USER + 1)).isFalse();
        assertThat(w.isOwnedBy(null)).isFalse();
    }
}
