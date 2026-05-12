package com.sseulang.domain.chat.application;

import com.sseulang.domain.chat.domain.EscrowApplicationView;

import java.util.Collection;
import java.util.Map;

public class NoOpEscrowApplicationView implements EscrowApplicationView {
    @Override
    public Map<Long, EscrowApplicationProjection> findActiveByChatRoomIds(Collection<Long> chatRoomIds) {
        return Map.of();
    }
}
