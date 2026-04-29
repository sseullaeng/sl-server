package com.sseulang.domain.payment.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    private static final Long USER = 1L;
    private static final String UID = "charge-abc-123";
    private static final long AMOUNT = 50_000L;

    @Test
    @DisplayName("startCharge 정상_status=대기, type=충전")
    void startCharge_정상() {
        Payment p = Payment.startCharge(USER, UID, AMOUNT);

        assertThat(p.getUserId()).isEqualTo(USER);
        assertThat(p.getMerchantUid()).isEqualTo(UID);
        assertThat(p.getAmount()).isEqualTo(AMOUNT);
        assertThat(p.getPaymentType()).isEqualTo(PaymentType.충전);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.대기);
        assertThat(p.getTransactionId()).isNull();
    }

    @Test
    @DisplayName("startCharge invalid 인자 거부")
    void startCharge_invalid() {
        assertThatThrownBy(() -> Payment.startCharge(null, UID, AMOUNT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.startCharge(0L, UID, AMOUNT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.startCharge(USER, "", AMOUNT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.startCharge(USER, UID, 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Payment.startCharge(USER, UID, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markAsPaid 정상_status=완료, true 반환")
    void markAsPaid_정상() {
        Payment p = Payment.startCharge(USER, UID, AMOUNT);
        boolean newlyPaid = p.markAsPaid("toss-key", PaymentMethod.CARD, LocalDateTime.now(), "{}");

        assertThat(newlyPaid).isTrue();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.완료);
        assertThat(p.getPaymentKey()).isEqualTo("toss-key");
        assertThat(p.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(p.getPaidAt()).isNotNull();
        assertThat(p.getRawResponse()).isEqualTo("{}");
    }

    @Test
    @DisplayName("markAsPaid 멱등_이미 완료_false 반환")
    void markAsPaid_멱등() {
        Payment p = Payment.startCharge(USER, UID, AMOUNT);
        p.markAsPaid("toss-key", PaymentMethod.CARD, LocalDateTime.now(), "{}");

        boolean second = p.markAsPaid("toss-key", PaymentMethod.CARD, LocalDateTime.now(), "{}");
        assertThat(second).isFalse();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.완료);
    }

    @Test
    @DisplayName("markAsPaid 실패 상태_PAYMENT_DUPLICATED")
    void markAsPaid_실패상태_거부() {
        Payment p = Payment.startCharge(USER, UID, AMOUNT);
        p.markAsFailed("test");
        assertThatThrownBy(() -> p.markAsPaid("k", PaymentMethod.CARD, LocalDateTime.now(), "{}"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_DUPLICATED);
    }

    @Test
    @DisplayName("markAsFailed 완료 상태_PAYMENT_DUPLICATED")
    void markAsFailed_완료_거부() {
        Payment p = Payment.startCharge(USER, UID, AMOUNT);
        p.markAsPaid("k", PaymentMethod.CARD, LocalDateTime.now(), "{}");
        assertThatThrownBy(() -> p.markAsFailed("retry"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_DUPLICATED);
    }

    @Test
    @DisplayName("verifyAmount 정상")
    void verifyAmount_정상() {
        Payment p = Payment.startCharge(USER, UID, AMOUNT);
        p.verifyAmount(AMOUNT);  // 예외 없음
    }

    @Test
    @DisplayName("verifyAmount mismatch_PAYMENT_AMOUNT_MISMATCH")
    void verifyAmount_mismatch() {
        Payment p = Payment.startCharge(USER, UID, AMOUNT);
        assertThatThrownBy(() -> p.verifyAmount(AMOUNT + 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }

    @Test
    @DisplayName("isOwnedBy")
    void isOwnedBy() {
        Payment p = Payment.startCharge(USER, UID, AMOUNT);
        assertThat(p.isOwnedBy(USER)).isTrue();
        assertThat(p.isOwnedBy(USER + 1)).isFalse();
        assertThat(p.isOwnedBy(null)).isFalse();
    }
}
