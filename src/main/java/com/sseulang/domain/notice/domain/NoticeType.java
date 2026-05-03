package com.sseulang.domain.notice.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * V1 schema notices.type ENUM 매핑. 한글 enum 이름은 DB ENUM 값과 동일해야 한다 (Hibernate
 * EnumType.STRING 으로 그대로 매핑).
 */
@Schema(description = "공지 유형 — 공지(일반) / 이벤트 / 새소식.")
public enum NoticeType {
    공지,
    이벤트,
    새소식
}
