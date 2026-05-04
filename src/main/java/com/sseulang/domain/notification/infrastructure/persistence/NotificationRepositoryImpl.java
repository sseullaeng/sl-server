package com.sseulang.domain.notification.infrastructure.persistence;

import com.sseulang.domain.notification.domain.Notification;
import com.sseulang.domain.notification.domain.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationMongoRepository mongo;
    private final MongoTemplate mongoTemplate;

    public NotificationRepositoryImpl(NotificationMongoRepository mongo, MongoTemplate mongoTemplate) {
        this.mongo = mongo;
        this.mongoTemplate = mongoTemplate;
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

    @Override
    public long markAllAsReadByUserId(Long userId) {
        Query q = new Query(Criteria.where("userId").is(userId).and("read").is(false));
        Update u = new Update().set("read", true);
        return mongoTemplate.updateMulti(q, u, Notification.class).getModifiedCount();
    }

    @Override
    public int saveAllIgnoreDuplicates(java.util.List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) return 0;
        // BulkOperations UNORDERED — 같은 chunk 안의 중복 한 건이 나머지 INSERT 를 차단하지 않음.
        // (broadcastId, userId) UNIQUE 위반은 silent skip — 중복 알림 차단 (round 12 멱등성).
        org.springframework.data.mongodb.core.BulkOperations bulk =
                mongoTemplate.bulkOps(
                        org.springframework.data.mongodb.core.BulkOperations.BulkMode.UNORDERED,
                        Notification.class
                );
        for (Notification n : notifications) {
            bulk.insert(n);
        }
        try {
            return bulk.execute().getInsertedCount();
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 부분 중복 — UNORDERED 라 정상 INSERT 는 이미 적용됨. 정확 카운트 조회 어려워 size 반환.
            // 운영 정합성에 영향 없음 (이미 적용된 건은 그대로, 중복은 차단).
            return notifications.size();
        }
    }
}
