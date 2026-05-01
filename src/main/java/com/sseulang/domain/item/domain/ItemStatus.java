package com.sseulang.domain.item.domain;

import io.swagger.v3.oas.annotations.media.Schema;
/**
 * 물품 상태. DB ENUM('판매중','예약','거래완료','비공개','삭제') 1:1 매핑.
 *
 * <p>거래 관련 상태(예약, 거래완료) 전이는 {@code transaction} 도메인이 트리거 — Item 자체로는
 * markAsHidden / markAsDeleted / restore 만 직접 노출.</p>
 */
@Schema(description = "물품 상태. 판매중→예약→거래완료. 비공개/삭제 는 별도.")
public enum ItemStatus {
    판매중,
    예약,
    거래완료,
    비공개,
    삭제;

    public boolean isEditable() {
        return this == 판매중 || this == 비공개;
    }

    public boolean isVisible() {
        return this == 판매중 || this == 예약 || this == 거래완료;
    }
}
