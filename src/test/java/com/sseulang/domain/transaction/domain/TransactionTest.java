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

    @Test
    @DisplayName("create 판매_정상_status=채팅중, deposit/rental null")
    void create_판매_정상() {
        Transaction t = Transaction.create(ITEM, SELLER, BUYER, TradeType.판매, 50_000L, null, null, null);

        assertThat(t.getItemId()).isEqualTo(ITEM);
        assertThat(t.getSellerId()).isEqualTo(SELLER);
        assertThat(t.getBuyerId()).isEqualTo(BUYER);
        assertThat(t.getTradeType()).isEqualTo(TradeType.판매);
        assertThat(t.getPrice()).isEqualTo(50_000L);
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.채팅중);
        assertThat(t.getReservedAt()).isNull();
        assertThat(t.getCompletedAt()).isNull();
        assertThat(t.getCanceledAt()).isNull();
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

    @Test
    @DisplayName("markAsReserved 정상_status=예약 + reservedAt")
    void reserve_정상() {
        Transaction t = sale();
        t.markAsReserved(NOW);
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.예약);
        assertThat(t.getReservedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("markAsReserved 채팅중 외_TRANSACTION_INVALID_STATE")
    void reserve_상태_위반() {
        Transaction t = sale();
        t.markAsReserved(NOW);
        assertThatThrownBy(() -> t.markAsReserved(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

    @Test
    @DisplayName("markAsCompleted 정상_예약→거래완료")
    void complete_정상() {
        Transaction t = sale();
        t.markAsReserved(NOW);
        t.markAsCompleted(NOW.plusHours(1));
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.거래완료);
        assertThat(t.getCompletedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    @DisplayName("markAsCompleted 채팅중에서 직행_거부")
    void complete_채팅중에서_거부() {
        Transaction t = sale();
        assertThatThrownBy(() -> t.markAsCompleted(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRANSACTION_INVALID_STATE);
    }

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
    @DisplayName("cancel 예약에서 정상")
    void cancel_예약() {
        Transaction t = sale();
        t.markAsReserved(NOW);
        t.cancel(NOW.plusHours(1), null);
        assertThat(t.getStatus()).isEqualTo(TransactionStatus.취소);
    }

    @Test
    @DisplayName("cancel 거래완료_거부")
    void cancel_거래완료_거부() {
        Transaction t = sale();
        t.markAsReserved(NOW);
        t.markAsCompleted(NOW.plusHours(1));
        assertThatThrownBy(() -> t.cancel(NOW.plusHours(2), null))
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
        return Transaction.create(ITEM, SELLER, BUYER, TradeType.판매, 50_000L, null, null, null);
    }
}
