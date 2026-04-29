package com.sseulang.domain.notification.infrastructure.persistence;

import com.sseulang.domain.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

interface NotificationMongoRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
