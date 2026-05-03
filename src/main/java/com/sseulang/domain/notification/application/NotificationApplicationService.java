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

    private final NotificationRepository notificationRepository;

    public NotificationApplicationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * 시스템 → 사용자 알림 발송. 다른 도메인이 호출 (예: 메시지 발신 시 상대방, 거래 상태 변경 시 등).
     */
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

    /**
     * 알림 읽음 처리. 본인 알림이 아니면 (또는 미존재면) <b>조용히 무시</b> — 다른 사용자 알림 id 가
     * 우연히 알려진 케이스에서도 실패 응답으로 정보 노출하지 않음. doc/FRONTEND_INTEGRATION.md §10.10 정합.
     * 본인 본인 알림이 이미 read 면 추가 SAVE 안 함 (멱등).
     */
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
}
