package com.sseulang.domain.transaction.domain;

import com.sseulang.domain.item.domain.TradeType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionTest {

    private static final Long ITEM = 10L;
    private static final Long SELLER = 100L;
    private static final Long BUYER = 200L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 1, 12, 0);
    private static final long PRICE = 50_000L;

    // ───────── create ─────────

    @Test
    @DisplayName("create 판매_정상_status=채팅중, deposit/rental null")
    void create_판매_정상() {
        Transaction t = Transaction.create(ITEM, SELLER, BUYER, TradeType.판매, PRICE, null, null, null);

        assertThat(t.getItemId()).isEqualTo(ITEM);
        assertThat(t.getSellerId()).isEqualTo(SELLER);
        assertThat(t.getBuyerId()).isEqualTo(BUYER);
        assertThat(t.getTradeType()).isEqualTo(TradeType.판매);
        assertThat(t.getPrice()).isEqualTo(PRICE);
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.채팅중);
        assertThat(t.getReservedAt()).isNull();
        assertThat(t.getHandoverConfirmedAt()).isNull();
        assertThat(t.getReceiveConfirmedAt()).isNull();
        assertThat(t.getCompletedAt()).isNull();
        assertThat(t.getCanceledAt()).isNull();
        assertThat(t.getEscrowHoldAmount()).isZero();
    }

    @Test
    @DisplayName("create 대여_정상_deposit/rental 박힘")
    void create_대여_정상() {
        LocalDateTime start = NOW.plusDays(1);
        LocalDateTime end = NOW.plusDays(7);
        Transaction t = Transaction.create(ITEM, SELLER, BUYER, TradeType.대여, 5_000L, 50_000L, start, end);

        assertThat(t.getDeposit()).isEqualTo(50_000L);
        assertThat(t.getRentalStart()).isEqualTo(start);
        assertThat(t.getRentalEnd()).isEqualTo(end);
    }

    @Test
    @DisplayName("create seller==buyer_거부")
    void create_self_거부() {
        assertThatThrownBy(() ->
                Transaction.create(ITEM, SELLER, SELLER, TradeType.판매, 1L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create 대여인데 deposit/rental 누락_거부")
    void create_대여_누락_거부() {
        assertThatThrownBy(() ->
                Transaction.create(ITEM, SELLER, BUYER, TradeType.대여, 1L, null, NOW, NOW.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                Transaction.create(ITEM, SELLER, BUYER, TradeType.대여, 1L, 10_000L, null, NOW.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create 판매인데 deposit/rental 박으면_거부")
    void create_판매_보증금_거부() {
        assertThatThrownBy(() ->
                Transaction.create(ITEM, SELLER, BUYER, TradeType.판매, 1L, 10_000L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                Transaction.create(ITEM, SELLER, BUYER, TradeType.판매, 1L, null, NOW, NOW.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create 음수 price/deposit_거부")
    void create_음수_거부() {
        assertThatThrownBy(() ->
                Transaction.create(ITEM, SELLER, BUYER, TradeType.판매, -1L, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                Transaction.create(ITEM, SELLER, BUYER, TradeType.대여, 1L, -1L, NOW, NOW.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create rentalEnd <= rentalStart_거부")
    void create_rental_역전_거부() {
        assertThatThrownBy(() ->
                Transaction.create(ITEM, SELLER, BUYER, TradeType.대여, 1L, 1_000L, NOW.plusDays(2), NOW.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                Transaction.create(ITEM, SELLER, BUYER, TradeType.대여, 1L, 1_000L, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ───────── markAsReserved ─────────

    @Test
    @DisplayName("markAsReserved(now) 나눔용_holdAmount=0")
    void reserve_old_signature_나눔() {
        Transaction t = sale();
        t.markAsReserved(NOW);

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.예약);
        assertThat(t.getReservedAt()).isEqualTo(NOW);
        assertThat(t.getEscrowHoldAmount()).isZero();
    }

    @Test
    @DisplayName("markAsReserved(now, holdAmount) 정상_escrowHoldAmount 동기 set (라운드 11)")
    void reserve_hold_정상() {
        Transaction t = sale();
        t.markAsReserved(NOW, PRICE);

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.예약);
        assertThat(t.getReservedAt()).isEqualTo(NOW);
        assertThat(t.getEscrowHoldAmount()).isEqualTo(PRICE);
    }

    @Test
    @DisplayName("markAsReserved 음수 holdAmount_거부")
    void reserve_음수_hold_거부() {
        Transaction t = sale();
        assertThatThrownBy(() -> t.markAsReserved(NOW, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markAsReserved 채팅중 외_TRANSACTION_INVALID_STATE")
    void reserve_상태_위반() {
        Transaction t = sale();
        t.markAsReserved(NOW, PRICE);
        assertThatThrownBy(() -> t.markAsReserved(NOW, PRICE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    // ───────── markHandover (라운드 11) ─────────

    @Test
    @DisplayName("markHandover 정상_예약→인계완료 + handoverConfirmedAt")
    void handover_정상() {
        Transaction t = sale();
        t.markAsReserved(NOW, PRICE);
        t.markHandover(NOW.plusHours(1));

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.인계완료);
        assertThat(t.getHandoverConfirmedAt()).isEqualTo(NOW.plusHours(1));
        assertThat(t.getReceiveConfirmedAt()).isNull();
        assertThat(t.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("markHandover 채팅중에서 직행_TRANSACTION_INVALID_STATE")
    void handover_채팅중_거부() {
        Transaction t = sale();
        assertThatThrownBy(() -> t.markHandover(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    @Test
    @DisplayName("markHandover 인계완료 후 재호출_TRANSACTION_INVALID_STATE (Aggregate 가드 — 멱등은 ApplicationService 책임)")
    void handover_재호출_거부() {
        Transaction t = sale();
        t.markAsReserved(NOW, PRICE);
        t.markHandover(NOW.plusHours(1));
        assertThatThrownBy(() -> t.markHandover(NOW.plusHours(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    // ───────── markReceived (라운드 11) ─────────

    @Test
    @DisplayName("markReceived 정상_인계완료→거래완료 + receiveConfirmedAt + completedAt 동기")
    void receive_정상() {
        Transaction t = sale();
        t.markAsReserved(NOW, PRICE);
        t.markHandover(NOW.plusHours(1));
        t.markReceived(NOW.plusHours(2));

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.거래완료);
        assertThat(t.getReceiveConfirmedAt()).isEqualTo(NOW.plusHours(2));
        assertThat(t.getCompletedAt()).isEqualTo(NOW.plusHours(2));
    }

    @Test
    @DisplayName("markReceived 예약 상태에서_TRANSACTION_INVALID_STATE (인계완료 거쳐야 함)")
    void receive_예약에서_거부() {
        Transaction t = sale();
        t.markAsReserved(NOW, PRICE);
        assertThatThrownBy(() -> t.markReceived(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    @Test
    @DisplayName("markReceived 채팅중에서 직행_TRANSACTION_INVALID_STATE")
    void receive_채팅중_거부() {
        Transaction t = sale();
        assertThatThrownBy(() -> t.markReceived(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    // ───────── cancel 단계별 (라운드 11) ─────────

    @Test
    @DisplayName("cancel 채팅중에서 정상")
    void cancel_채팅중() {
        Transaction t = sale();
        t.cancel(NOW, "사유");
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.취소);
        assertThat(t.getCanceledAt()).isEqualTo(NOW);
        assertThat(t.getCancelReason()).isEqualTo("사유");
    }

    @Test
    @DisplayName("cancel 예약에서 정상_escrowHoldAmount 보존 (환불 금액 추적)")
    void cancel_예약() {
        Transaction t = sale();
        t.markAsReserved(NOW, PRICE);
        t.cancel(NOW.plusHours(1), null);

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.취소);
        assertThat(t.getEscrowHoldAmount()).isEqualTo(PRICE);  // 보존 — ApplicationService 가 환불 시 참조
    }

    @Test
    @DisplayName("cancel 인계완료 이후_TRANSACTION_INVALID_STATE (라운드 11 — R2 분쟁 영역)")
    void cancel_인계완료_거부() {
        Transaction t = sale();
        t.markAsReserved(NOW, PRICE);
        t.markHandover(NOW.plusHours(1));

        assertThatThrownBy(() -> t.cancel(NOW.plusHours(2), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    @Test
    @DisplayName("cancel 거래완료 후_거부")
    void cancel_거래완료_거부() {
        Transaction t = sale();
        t.markAsReserved(NOW, PRICE);
        t.markHandover(NOW.plusHours(1));
        t.markReceived(NOW.plusHours(2));

        assertThatThrownBy(() -> t.cancel(NOW.plusHours(3), null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    @Test
    @DisplayName("cancel 이미 취소_거부")
    void cancel_중복_거부() {
        Transaction t = sale();
        t.cancel(NOW, null);
        assertThatThrownBy(() -> t.cancel(NOW.plusHours(1), null))
                .isInstanceOf(BusinessException.class);
    }

    // ───────── helpers ─────────

    @Test
    @DisplayName("isSeller / isBuyer / isParticipant")
    void participants() {
        Transaction t = sale();
        assertThat(t.isSeller(SELLER)).isTrue();
        assertThat(t.isBuyer(BUYER)).isTrue();
        assertThat(t.isParticipant(SELLER)).isTrue();
        assertThat(t.isParticipant(BUYER)).isTrue();
        assertThat(t.isParticipant(999L)).isFalse();
        assertThat(t.isSeller(BUYER)).isFalse();
    }

    private static Transaction sale() {
        return Transaction.create(ITEM, SELLER, BUYER, TradeType.판매, PRICE, null, null, null);
    }
}
