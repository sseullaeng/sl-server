package com.sseulang.domain.chat.application;

import com.sseulang.domain.chat.domain.ChatRoom;
import com.sseulang.domain.chat.domain.ChatRoomRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryFakeChatRoomRepository implements ChatRoomRepository {

    private final Map<Long, ChatRoom> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Optional<ChatRoom> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<ChatRoom> findByItemAndUsers(Long itemId, Long userA, Long userB) {
        long u1 = Math.min(userA, userB);
        long u2 = Math.max(userA, userB);
        return store.values().stream()
                .filter(c -> c.getItemId().equals(itemId)
                        && c.getUser1Id() == u1 && c.getUser2Id() == u2)
                .findFirst();
    }

    @Override
    public Page<ChatRoom> findMine(Long userId, Pageable pageable) {
        List<ChatRoom> mine = store.values().stream()
                .filter(c -> c.isParticipant(userId))
                .sorted(Comparator
                        .comparing((ChatRoom c) -> c.getLastMessageAt() == null ? 1 : 0)
                        .thenComparing(c -> c.getLastMessageAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparingLong(ChatRoom::getId).reversed())
                .toList();
        int start = Math.min((int) pageable.getOffset(), mine.size());
        int end = Math.min(start + pageable.getPageSize(), mine.size());
        return new PageImpl<>(mine.subList(start, end), pageable, mine.size());
    }

    @Override
    public ChatRoom save(ChatRoom chatRoom) {
        if (chatRoom.getId() == null) {
            ReflectionTestUtils.setField(chatRoom, "id", ++sequence);
        }
        store.put(chatRoom.getId(), chatRoom);
        return chatRoom;
    }
}
