package com.sseulang.domain.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(String id);

    /** userId 기준 최신순 페이징. */
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    /**
     * 본인 unread 알림 모두 read 처리 (atomic UPDATE). 처리된 건수 반환 — 0 도 정상.
     */
    long markAllAsReadByUserId(Long userId);
}
