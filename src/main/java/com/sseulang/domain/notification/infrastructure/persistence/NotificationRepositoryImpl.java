package com.sseulang.domain.notification.infrastructure.persistence;

import com.sseulang.domain.notification.domain.Notification;
import com.sseulang.domain.notification.domain.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationMongoRepository mongo;

    public NotificationRepositoryImpl(NotificationMongoRepository mongo) {
        this.mongo = mongo;
    }

    @Override
    public Notification save(Notification notification) {
        return mongo.save(notification);
    }

    @Override
    public Optional<Notification> findById(String id) {
        return mongo.findById(id);
    }

    @Override
    public Page<Notification> findByUserId(Long userId, Pageable pageable) {
        return mongo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
