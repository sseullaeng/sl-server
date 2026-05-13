package com.sseulang.domain.notification.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(String id);

    
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    

    long markAllAsReadByUserId(Long userId);

    long countUnreadByUserId(Long userId);



    int saveAllIgnoreDuplicates(java.util.List<Notification> notifications);
}
