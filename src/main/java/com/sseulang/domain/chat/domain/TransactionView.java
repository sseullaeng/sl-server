package com.sseulang.domain.chat.domain;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

// 채팅방 카드용 — 거래 상태 동적 조회 포트. 어댑터는 transaction/infrastructure 에 위치.
public interface TransactionView {

    Map<Long, TransactionProjection> findActiveByChatRoomIds(Collection<Long> chatRoomIds);

    // V43 — 대여 거래 한정. rentalStart/End 노출하여 FE 가 chat-rooms 응답만으로 기간 표시 가능.
    record TransactionProjection(
            Long transactionId,
            String status,
            LocalDateTime rentalStart,
            LocalDateTime rentalEnd
    ) { }
}
