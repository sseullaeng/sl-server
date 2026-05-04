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

    /**
     * Round 12 — broadcast 청크 bulk INSERT. (broadcastId, userId) UNIQUE 위반 ({@link
     * org.springframework.dao.DuplicateKeyException}) 은 silent skip — 중복 알림 생성 차단.
     * 실제 INSERT 된 건수 반환.
     *
     * <p>Mongo unordered insert — 한 chunk 안의 중복 한 건이 나머지 INSERT 를 차단하지 않음.</p>
     */
    int saveAllIgnoreDuplicates(java.util.List<Notification> notifications);
}
