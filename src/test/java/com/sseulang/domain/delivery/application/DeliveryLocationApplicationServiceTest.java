package com.sseulang.domain.delivery.application;

import com.sseulang.domain.delivery.domain.DeliveryLocation;
import com.sseulang.domain.delivery.domain.DeliveryLocationCache;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import com.sseulang.global.websocket.RealtimePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DeliveryLocationApplicationServiceTest {

    private static final Long DELIVERY = 100L;
    private static final Long RIDER = 200L;

    private DeliveryApplicationService deliverySvc;
    private DeliveryLocationCache cache;
    private RealtimePublisher publisher;
    private DeliveryLocationApplicationService service;
    private Instant fixed;

    @BeforeEach
    void setUp() {
        deliverySvc = mock(DeliveryApplicationService.class);
        cache = mock(DeliveryLocationCache.class);
        publisher = mock(RealtimePublisher.class);
        fixed = Instant.parse("2026-05-03T10:00:00Z");
        Clock clock = Clock.fixed(fixed, ZoneId.of("Asia/Seoul"));
        service = new DeliveryLocationApplicationService(deliverySvc, cache, publisher, clock);
    }

    @Test
    @DisplayName("종료 상태_DELIVERY_INVALID_STATE → 캐시 save / broadcast 모두 X")
    void 종료_상태_거부() {
        doThrow(new BusinessException(ErrorCode.DELIVERY_INVALID_STATE))
                .when(deliverySvc).requireRiderTrackable(DELIVERY, RIDER);

        assertThatThrownBy(() -> service.publishLocation(DELIVERY, RIDER, 37.5, 127.0, null, fixed))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_INVALID_STATE);

        verify(cache, never()).save(any(), any());
        verify(publisher, never()).publishDeliveryLocation(any(), any());
    }

    @Test
    @DisplayName("정상 publish_캐시 + broadcast 둘 다 호출")
    void 정상_publish() {
        service.publishLocation(DELIVERY, RIDER, 37.5, 127.0, 8.5, fixed);

        verify(deliverySvc, times(1)).requireRiderTrackable(DELIVERY, RIDER);
        verify(cache, times(1)).save(eq(DELIVERY), any(DeliveryLocation.class));
        verify(publisher, times(1)).publishDeliveryLocation(eq(DELIVERY), any(DeliveryLocation.class));
    }

    @Test
    @DisplayName("rate limit_같은 (delivery, rider) 1초 내 두 번째 호출_DELIVERY_LOCATION_TOO_FREQUENT")
    void rate_limit_거부() {
        service.publishLocation(DELIVERY, RIDER, 37.5, 127.0, null, fixed);

        // 즉시 두 번째 호출 (같은 시각) — 1초 미만 간격
        assertThatThrownBy(() -> service.publishLocation(DELIVERY, RIDER, 37.5, 127.0, null, fixed))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_LOCATION_TOO_FREQUENT);
    }

    @Test
    @DisplayName("findLast_종료 상태이면 거부")
    void findLast_종료_거부() {
        doThrow(new BusinessException(ErrorCode.DELIVERY_INVALID_STATE))
                .when(deliverySvc).requireParticipantTrackable(DELIVERY, RIDER);

        assertThatThrownBy(() -> service.findLast(DELIVERY, RIDER))
                .isInstanceOf(BusinessException.class);

        verify(cache, never()).findLast(any());
    }

    @Test
    @DisplayName("recordedAt drift 60초 초과 미래_서버 시각으로 정정 (예외 X)")
    void drift_초과_정정() {
        Instant farFuture = fixed.plusSeconds(120);
        service.publishLocation(DELIVERY, RIDER, 37.5, 127.0, null, farFuture);

        // save 는 호출됐고, broadcast 도 호출됨 (예외 안 남)
        verify(cache, times(1)).save(eq(DELIVERY), any(DeliveryLocation.class));
        verify(publisher, times(1)).publishDeliveryLocation(eq(DELIVERY), any(DeliveryLocation.class));
    }
}
