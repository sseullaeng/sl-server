package com.sseulang.domain.notification.application;

import com.sseulang.domain.notification.application.dto.NotificationResult;
import com.sseulang.domain.notification.domain.Notification;
import com.sseulang.domain.notification.domain.NotificationRepository;
import com.sseulang.domain.notification.domain.NotificationType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationApplicationService {

    
    private static final int BROADCAST_CHUNK_SIZE = 500;

    private final NotificationRepository notificationRepository;
    private final com.sseulang.domain.user.domain.UserRepository userRepository;

    public NotificationApplicationService(
            NotificationRepository notificationRepository,
            com.sseulang.domain.user.domain.UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    

    public Notification notify(
            Long userId,
            NotificationType type,
            String title,
            String content,
            String linkType,
            Long linkId
    ) {
        return notificationRepository.save(
                Notification.create(userId, type, title, content, linkType, linkId)
        );
    }

    public Page<NotificationResult> listMine(Long userId, Pageable pageable) {
        return notificationRepository.findByUserId(userId, pageable).map(NotificationResult::from);
    }

    

    public void markAsRead(String notificationId, Long requesterId) {
        Notification n = notificationRepository.findById(notificationId).orElse(null);
        if (n == null || !n.isOwnedBy(requesterId)) {
            return;
        }
        if (!n.isRead()) {
            n.markAsRead();
            notificationRepository.save(n);
        }
    }

    
    public long markAllAsRead(Long userId) {
        return notificationRepository.markAllAsReadByUserId(userId);
    }

    public long countUnread(Long userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    

    public BroadcastResult broadcast(String title, String content, String idempotencyKey) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 은 필수입니다");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 는 필수입니다");
        }
        String broadcastId = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : java.util.UUID.randomUUID().toString();
        long total = 0;
        long afterId = 0;
        while (true) {
            java.util.List<Long> chunk = userRepository.findActiveIdsAfter(afterId, BROADCAST_CHUNK_SIZE);
            if (chunk.isEmpty()) break;
            java.util.List<Notification> docs = new java.util.ArrayList<>(chunk.size());
            for (Long uid : chunk) {
                docs.add(Notification.create(uid, NotificationType.공지, title, content, null, null, broadcastId));
            }
            total += notificationRepository.saveAllIgnoreDuplicates(docs);
            afterId = chunk.get(chunk.size() - 1);
            if (chunk.size() < BROADCAST_CHUNK_SIZE) break;
        }
        return new BroadcastResult(broadcastId, total);
    }

    
    public record BroadcastResult(String broadcastId, long sent) { }
}
