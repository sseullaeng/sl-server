package com.sseulang.domain.delivery.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryRequestTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 30, 12, 0);
    private static final Long REQUESTER = 1L;
    private static final Long RIDER = 2L;

    @Test
    @DisplayName("create 정상_모집중 + 필드 채워짐")
    void create_정상() {
        DeliveryRequest d = newDelivery();

        assertThat(d.getRequesterId()).isEqualTo(REQUESTER);
        assertThat(d.getRiderId()).isNull();
        assertThat(d.getPickupAddress()).isEqualTo("서울 강남구");
        assertThat(d.getDropoffAddress()).isEqualTo("서울 송파구");
        assertThat(d.getItemDescription()).isEqualTo("서류");
        assertThat(d.getFee()).isEqualTo(5000L);
        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.모집중);
        assertThat(d.getRequestedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("create invalid 인자 거부")
    void create_invalid() {
        assertThatThrownBy(() -> DeliveryRequest.create(
                null, "p", "d", "i", 1000L, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryRequest.create(
                REQUESTER, "", "d", "i", 1000L, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryRequest.create(
                REQUESTER, "p", "d", "i", 0L, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryRequest.create(
                REQUESTER, "p", "d", "i", -1L, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeliveryRequest.create(
                REQUESTER, "p", "d", "i", 1000L, NOW.minusHours(1), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("acceptBy 정상_모집중→수락_riderId·acceptedAt 세팅")
    void acceptBy_정상() {
        DeliveryRequest d = newDelivery();

        d.acceptBy(RIDER, NOW.plusMinutes(5));

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.수락);
        assertThat(d.getRiderId()).isEqualTo(RIDER);
        assertThat(d.getAcceptedAt()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    @DisplayName("acceptBy 본인 거래_DELIVERY_SELF_NOT_ALLOWED")
    void acceptBy_본인_거부() {
        DeliveryRequest d = newDelivery();

        assertThatThrownBy(() -> d.acceptBy(REQUESTER, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_SELF_NOT_ALLOWED);
    }

    @Test
    @DisplayName("acceptBy 모집중_아닌_상태_DELIVERY_INVALID_STATE")
    void acceptBy_invalid_state() {
        DeliveryRequest d = newDelivery();
        d.acceptBy(RIDER, NOW);

        assertThatThrownBy(() -> d.acceptBy(3L, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_INVALID_STATE);
    }

    @Test
    @DisplayName("정상 흐름_모집중→수락→배송중→배송완료→정산완료")
    void 정상_흐름() {
        DeliveryRequest d = newDelivery();

        d.acceptBy(RIDER, NOW.plusMinutes(5));
        d.markPickedUp(NOW.plusMinutes(15));
        d.markDelivered(NOW.plusMinutes(45));
        d.markSettled(NOW.plusMinutes(50));

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.정산완료);
        assertThat(d.getPickedUpAt()).isEqualTo(NOW.plusMinutes(15));
        assertThat(d.getDeliveredAt()).isEqualTo(NOW.plusMinutes(45));
        assertThat(d.getCompletedAt()).isEqualTo(NOW.plusMinutes(50));
    }

    @Test
    @DisplayName("markPickedUp 수락 아닌 상태_거부")
    void markPickedUp_invalid() {
        DeliveryRequest d = newDelivery();

        assertThatThrownBy(() -> d.markPickedUp(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_INVALID_STATE);
    }

    @Test
    @DisplayName("markDelivered 배송중 아닌 상태_거부")
    void markDelivered_invalid() {
        DeliveryRequest d = newDelivery();
        d.acceptBy(RIDER, NOW);

        assertThatThrownBy(() -> d.markDelivered(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_INVALID_STATE);
    }

    @Test
    @DisplayName("markSettled 배송완료 아닌 상태_거부")
    void markSettled_invalid() {
        DeliveryRequest d = newDelivery();
        d.acceptBy(RIDER, NOW);
        d.markPickedUp(NOW);

        // 배송중 상태에서 정산 시도
        assertThatThrownBy(() -> d.markSettled(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_INVALID_STATE);
    }

    @Test
    @DisplayName("cancelByRequester 모집중_취소")
    void cancel_정상() {
        DeliveryRequest d = newDelivery();

        d.cancelByRequester(NOW.plusMinutes(2), "급해서");

        assertThat(d.getStatus()).isEqualTo(DeliveryStatus.취소);
        assertThat(d.getCanceledAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(d.getCancelReason()).isEqualTo("급해서");
    }

    @Test
    @DisplayName("cancelByRequester 수락 이후_거부 (5/6 이전 단순화 정책)")
    void cancel_수락이후_거부() {
        DeliveryRequest d = newDelivery();
        d.acceptBy(RIDER, NOW);

        assertThatThrownBy(() -> d.cancelByRequester(NOW.plusMinutes(1), "변심"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_INVALID_STATE);
    }

    @Test
    @DisplayName("isParticipant — 요청자/라이더 본인만 true")
    void isParticipant() {
        DeliveryRequest d = newDelivery();
        d.acceptBy(RIDER, NOW);

        assertThat(d.isParticipant(REQUESTER)).isTrue();
        assertThat(d.isParticipant(RIDER)).isTrue();
        assertThat(d.isParticipant(999L)).isFalse();
        assertThat(d.isParticipant(null)).isFalse();
    }

    @Test
    @DisplayName("isRider — 라이더 미수락 상태에서는 false")
    void isRider_미수락() {
        DeliveryRequest d = newDelivery();

        assertThat(d.isRider(REQUESTER)).isFalse();
        assertThat(d.isRider(RIDER)).isFalse();
    }

    @Test
    @DisplayName("isTerminal — 정산완료/취소 만 종료 상태")
    void status_terminal() {
        assertThat(DeliveryStatus.모집중.isTerminal()).isFalse();
        assertThat(DeliveryStatus.수락.isTerminal()).isFalse();
        assertThat(DeliveryStatus.배송중.isTerminal()).isFalse();
        assertThat(DeliveryStatus.배송완료.isTerminal()).isFalse();
        assertThat(DeliveryStatus.정산완료.isTerminal()).isTrue();
        assertThat(DeliveryStatus.취소.isTerminal()).isTrue();
    }

    private static DeliveryRequest newDelivery() {
        return DeliveryRequest.create(
                REQUESTER, "서울 강남구", "서울 송파구", "서류",
                5000L, NOW.plusHours(2), "1층 로비", NOW);
    }
}
