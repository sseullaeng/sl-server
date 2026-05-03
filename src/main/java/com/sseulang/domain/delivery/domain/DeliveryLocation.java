package com.sseulang.domain.delivery.domain;

import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;

import java.time.Instant;

/**
 * 라이더 실시간 위치 VO. WGS84 좌표 + 정확도 + 기록 시각.
 *
 * <p>DB 저장 X — Redis 휘발 캐시 (TTL 30분). 마지막 위치만 보존하므로 history 없음.
 * 좌표 범위 검증은 생성 시 강제. 위경도가 한국 범위(33~39N / 124~132E) 밖이면
 * {@link ErrorCode#DELIVERY_LOCATION_INVALID} — GPS 노이즈 / 변조 차단.</p>
 *
 * @param latitude   위도 (33.0 ~ 39.0, 한국 범위)
 * @param longitude  경도 (124.0 ~ 132.0, 한국 범위)
 * @param accuracyM  GPS 정확도 (m). null 허용. ≥ 0
 * @param recordedAt 라이더 디바이스 측 기록 시각. null 허용 — 서버가 채움
 */
public record DeliveryLocation(
        double latitude,
        double longitude,
        Double accuracyM,
        Instant recordedAt
) {

    /** WGS84 위경도 한국 범위 (서비스 영역). 해외 배달 도입 시 확장. */
    private static final double LAT_MIN = 33.0;
    private static final double LAT_MAX = 39.0;
    private static final double LNG_MIN = 124.0;
    private static final double LNG_MAX = 132.0;

    public DeliveryLocation {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)
                || latitude < LAT_MIN || latitude > LAT_MAX
                || longitude < LNG_MIN || longitude > LNG_MAX) {
            throw new BusinessException(ErrorCode.DELIVERY_LOCATION_INVALID);
        }
        if (accuracyM != null && (accuracyM.isNaN() || accuracyM.isInfinite() || accuracyM < 0)) {
            throw new BusinessException(ErrorCode.DELIVERY_LOCATION_INVALID);
        }
    }
}
