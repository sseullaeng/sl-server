package com.sseulang.domain.notice.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공지 유형 — 공지(일반) / 이벤트 / 새소식.")
public enum NoticeType {
    공지,
    이벤트,
    새소식
}
