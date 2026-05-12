package com.sseulang.domain.chat.domain;

import java.util.Collection;
import java.util.Map;

// 채팅방 카드용 — 거래 상태 동적 조회 포트. 어댑터는 transaction/infrastructure 에 위치.
public interface TransactionView {

    Map<Long, TransactionProjection> findActiveByChatRoomIds(Collection<Long> chatRoomIds);

    record TransactionProjection(Long transactionId, String status) { }
}
