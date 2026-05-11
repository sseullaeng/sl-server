package com.sseulang.domain.notification.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "notifications")
@CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")

@CompoundIndex(name = "broadcast_user_unique",
        def = "{'broadcastId': 1, 'userId': 1}", unique = true, sparse = true)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    private static final int TITLE_MAX = 100;
    private static final int CONTENT_MAX = 500;

    @Id
    private String id;

    @Field("userId")
    @Indexed
    private Long userId;

    @Field("type")
    private NotificationType type;

    @Field("title")
    private String title;

    @Field("content")
    private String content;

    @Field("linkType")
    private String linkType;

    @Field("linkId")
    private Long linkId;

    @Field("read")
    private boolean read;

    

    @Field("broadcastId")
    private String broadcastId;

    

    @Field("category")
    private NotificationCategory category;

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    public static Notification create(
            Long userId,
            NotificationType type,
            String title,
            String content,
            String linkType,
            Long linkId
    ) {
        return create(userId, type, title, content, linkType, linkId, null, NotificationCategory.USER);
    }

    
    public static Notification create(
            Long userId,
            NotificationType type,
            String title,
            String content,
            String linkType,
            Long linkId,
            String broadcastId
    ) {
        
        return create(userId, type, title, content, linkType, linkId, broadcastId, NotificationCategory.SYSTEM);
    }

    
    public static Notification create(
            Long userId,
            NotificationType type,
            String title,
            String content,
            String linkType,
            Long linkId,
            String broadcastId,
            NotificationCategory category
    ) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 는 양수여야 합니다");
        }
        if (type == null) {
            throw new IllegalArgumentException("type 은 필수입니다");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 은 비어있을 수 없습니다");
        }
        if (title.length() > TITLE_MAX) {
            throw new IllegalArgumentException("title 은 " + TITLE_MAX + "자를 초과할 수 없습니다");
        }
        if (content != null && content.length() > CONTENT_MAX) {
            throw new IllegalArgumentException("content 는 " + CONTENT_MAX + "자를 초과할 수 없습니다");
        }
        Notification n = new Notification();
        n.userId = userId;
        n.type = type;
        n.title = title;
        n.content = content;
        n.linkType = linkType;
        n.linkId = linkId;
        n.read = false;
        n.broadcastId = broadcastId;
        n.category = category != null ? category : NotificationCategory.USER;
        return n;
    }

    public void markAsRead() {
        this.read = true;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.userId);
    }
}
