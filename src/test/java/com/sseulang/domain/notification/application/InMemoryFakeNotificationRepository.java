package com.sseulang.domain.notification.application;

import com.sseulang.domain.notification.domain.Notification;
import com.sseulang.domain.notification.domain.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryFakeNotificationRepository implements NotificationRepository {

    private final Map<String, Notification> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Notification save(Notification notification) {
        if (notification.getId() == null) {
            String id = String.format("%024d", ++sequence);
            ReflectionTestUtils.setField(notification, "id", id);
            ReflectionTestUtils.setField(notification, "createdAt", Instant.now());
        }
        store.put(notification.getId(), notification);
        return notification;
    }

    @Override
    public Optional<Notification> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Page<Notification> findByUserId(Long userId, Pageable pageable) {
        List<Notification> mine = store.values().stream()
                .filter(n -> n.getUserId().equals(userId))
                .sorted(Comparator.comparing(Notification::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int start = Math.min((int) pageable.getOffset(), mine.size());
        int end = Math.min(start + pageable.getPageSize(), mine.size());
        return new PageImpl<>(mine.subList(start, end), pageable, mine.size());
    }
}
