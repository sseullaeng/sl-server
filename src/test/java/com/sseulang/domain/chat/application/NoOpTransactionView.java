package com.sseulang.domain.chat.application;

import com.sseulang.domain.chat.domain.TransactionView;

import java.util.Collection;
import java.util.Map;

public class NoOpTransactionView implements TransactionView {
    @Override
    public Map<Long, TransactionProjection> findActiveByChatRoomIds(Collection<Long> chatRoomIds) {
        return Map.of();
    }
}
