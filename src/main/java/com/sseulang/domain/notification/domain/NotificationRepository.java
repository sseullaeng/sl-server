package com.sseulang.domain.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(String id);

    /** userId 기준 최신순 페이징. */
    Page<Notification> findByUserId(Long userId, Pageable pageable);
}
