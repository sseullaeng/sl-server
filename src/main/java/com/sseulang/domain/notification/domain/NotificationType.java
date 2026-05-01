package com.sseulang.domain.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/** 알림 유형 — 가이드 §4.10 시스템 → 사용자 단방향. */
@Schema(description = "알림 유형 — 메시지 / 거래 / 리뷰 / 공지 / 시스템.")
public enum NotificationType {
    메시지,
    거래,
    리뷰,
    공지,
    시스템
}
