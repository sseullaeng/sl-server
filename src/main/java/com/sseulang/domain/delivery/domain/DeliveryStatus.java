package com.sseulang.domain.delivery.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "배달 상태. 모집중→수락→배송중→배송완료→정산완료 (또는 취소).")
public enum DeliveryStatus {
    모집중,
    수락,
    배송중,
    배송완료,
    정산완료,
    취소;

    public boolean isTerminal() {
        return this == 정산완료 || this == 취소;
    }

    
    public boolean canAccept() {
        return this == 모집중;
    }

    
    public boolean canPickup() {
        return this == 수락;
    }

    
    public boolean canDeliver() {
        return this == 배송중;
    }

    
    public boolean canSettle() {
        return this == 배송완료;
    }

    

    public boolean canRequesterCancel() {
        return this == 모집중;
    }

    

    public boolean canTrackLocation() {
        return this == 수락 || this == 배송중;
    }
}
