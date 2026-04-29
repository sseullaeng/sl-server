package com.sseulang.domain.item.domain;

/**
 * 대여 단위. DB는 VARCHAR(20) — '시간 | 일 | 주 | 월'. {@code @Enumerated(EnumType.STRING)} 으로 그대로 저장.
 */
public enum RentalUnit {
    시간,
    일,
    주,
    월
}
