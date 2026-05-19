package com.sseulang.domain.delivery.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryLocationTest {

    @Test
    @DisplayName("정상 한국 좌표_생성 OK")
    void 정상_생성() {
        DeliveryLocation loc = new DeliveryLocation(37.4979, 127.0276, 8.5, Instant.parse("2026-05-03T10:00:00Z"));
        assertThat(loc.latitude()).isEqualTo(37.4979);
        assertThat(loc.longitude()).isEqualTo(127.0276);
        assertThat(loc.accuracyM()).isEqualTo(8.5);
    }

    @Test
    @DisplayName("위도 한국 범위 밖_DELIVERY_LOCATION_INVALID")
    void 위도_범위밖_거부() {
        assertThatThrownBy(() -> new DeliveryLocation(45.0, 127.0276, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DELIVERY_LOCATION_INVALID);
    }

    @Test
    @DisplayName("경도 한국 범위 밖_DELIVERY_LOCATION_INVALID")
    void 경도_범위밖_거부() {
        assertThatThrownBy(() -> new DeliveryLocation(37.5, 100.0, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("NaN 좌표_거부")
    void NaN_거부() {
        assertThatThrownBy(() -> new DeliveryLocation(Double.NaN, 127.0, null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("음수 accuracy_거부")
    void accuracy_음수_거부() {
        assertThatThrownBy(() -> new DeliveryLocation(37.5, 127.0, -1.0, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("accuracy null_허용")
    void accuracy_null_OK() {
        DeliveryLocation loc = new DeliveryLocation(37.5, 127.0, null, null);
        assertThat(loc.accuracyM()).isNull();
    }
}
