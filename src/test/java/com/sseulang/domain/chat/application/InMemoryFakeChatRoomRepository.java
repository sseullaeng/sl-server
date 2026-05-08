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
                .filter(c -> c.isParticipant(userId) && !c.iLeft(userId))
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

    @Override
    public int recordIncomingMessage(Long chatRoomId, Long senderId, String preview) {
        ChatRoom room = store.get(chatRoomId);
        if (room == null) return 0;
        ReflectionTestUtils.setField(room, "lastMessage", preview);
        ReflectionTestUtils.setField(room, "lastMessageAt", java.time.LocalDateTime.now());
        if (senderId.equals(room.getUser1Id())) {
            ReflectionTestUtils.setField(room, "user2Unread", room.getUser2Unread() + 1);
        } else if (senderId.equals(room.getUser2Id())) {
            ReflectionTestUtils.setField(room, "user1Unread", room.getUser1Unread() + 1);
        }
        return 1;
    }

    @Override
    public int markAsRead(Long chatRoomId, Long userId) {
        ChatRoom room = store.get(chatRoomId);
        if (room == null || !room.isParticipant(userId)) return 0;
        if (userId.equals(room.getUser1Id())) {
            ReflectionTestUtils.setField(room, "user1Unread", 0);
        } else {
            ReflectionTestUtils.setField(room, "user2Unread", 0);
        }
        return 1;
    }

    @Override
    public int markAsLeft(Long chatRoomId, Long userId) {
        ChatRoom room = store.get(chatRoomId);
        if (room == null || !room.isParticipant(userId)) return 0;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (userId.equals(room.getUser1Id())) {
            if (room.getUser1LeftAt() == null) {
                ReflectionTestUtils.setField(room, "user1LeftAt", now);
            }
        } else {
            if (room.getUser2LeftAt() == null) {
                ReflectionTestUtils.setField(room, "user2LeftAt", now);
            }
        }
        return 1;
    }
}
