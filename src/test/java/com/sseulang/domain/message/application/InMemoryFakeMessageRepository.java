package com.sseulang.domain.message.application;

import com.sseulang.domain.message.domain.Message;
import com.sseulang.domain.message.domain.MessageRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryFakeMessageRepository implements MessageRepository {

    private final Map<String, Message> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Message save(Message message) {
        if (message.getId() == null) {
            String id = String.format("%024d", ++sequence);  // ObjectId 흉내
            ReflectionTestUtils.setField(message, "id", id);
            ReflectionTestUtils.setField(message, "createdAt", Instant.now());
        }
        store.put(message.getId(), message);
        return message;
    }

    @Override
    public List<Message> findPage(Long chatRoomId, String beforeId, int size) {
        return store.values().stream()
                .filter(m -> m.getChatRoomId().equals(chatRoomId))
                .filter(m -> beforeId == null || beforeId.isBlank() || m.getId().compareTo(beforeId) < 0)
                .sorted(Comparator.comparing(Message::getId).reversed())
                .limit(size)
                .toList();
    }
}
