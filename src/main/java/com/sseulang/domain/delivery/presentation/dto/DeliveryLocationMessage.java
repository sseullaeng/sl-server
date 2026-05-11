package com.sseulang.domain.delivery.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "배달 실시간 위치 — 한국 위경도 범위 강제. accuracy/recordedAt 옵션.")
public record DeliveryLocationMessage(
        @Schema(example = "37.4979", description = "WGS84 위도 (33.0~39.0)")
        double latitude,

        @Schema(example = "127.0276", description = "WGS84 경도 (124.0~132.0)")
        double longitude,

        @Schema(example = "8.5", description = "GPS 정확도 (m). 옵션", nullable = true)
        Double accuracyM,

        @Schema(example = "2026-05-03T10:00:00.123Z",
                description = "라이더 디바이스 기록 시각 (ISO Instant). 미주입 시 서버 시각", nullable = true)
        Instant recordedAt
) { }
