package com.sseulang.domain.chat.domain;

import java.util.Collection;
import java.util.Map;

// 채팅방 카드용 — 거래대행 상태 동적 조회 포트. 어댑터는 escrow/infrastructure 에 위치.
public interface EscrowApplicationView {

    Map<Long, EscrowApplicationProjection> findActiveByChatRoomIds(Collection<Long> chatRoomIds);

    record EscrowApplicationProjection(Long escrowApplicationId, String status, Long deliveryId) { }
}
