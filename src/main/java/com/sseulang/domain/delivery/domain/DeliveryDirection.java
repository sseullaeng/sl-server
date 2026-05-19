package com.sseulang.domain.delivery.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "배달 방향. FORWARD = seller→buyer (기본), RETURN = buyer→seller 반환 (대여 거래대행 한정).")
public enum DeliveryDirection {
    FORWARD,
    RETURN
}
