package com.sseulang.domain.transaction.infrastructure;

import com.sseulang.domain.chat.domain.TransactionView;
import com.sseulang.domain.transaction.domain.Transaction;
import com.sseulang.domain.transaction.domain.TransactionRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
public class TransactionViewAdapter implements TransactionView {

    private final TransactionRepository repository;

    public TransactionViewAdapter(TransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<Long, TransactionProjection> findActiveByChatRoomIds(Collection<Long> chatRoomIds) {
        if (chatRoomIds == null || chatRoomIds.isEmpty()) return Map.of();
        Map<Long, TransactionProjection> map = new HashMap<>();
        for (Transaction t : repository.findLatestNonCanceledByChatRoomIdIn(chatRoomIds)) {
            map.put(t.getChatRoomId(),
                    new TransactionProjection(
                            t.getId(), t.getStatus().name(),
                            t.getRentalStart(), t.getRentalEnd()
                    ));
        }
        return map;
    }
}
