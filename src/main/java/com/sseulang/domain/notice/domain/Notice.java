package com.sseulang.domain.notice.domain;

import com.sseulang.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseEntity {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int IMAGE_URL_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NoticeType type;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_url", length = IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    public static Notice create(
            Long adminId,
            NoticeType type,
            String title,
            String content,
            String imageUrl,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        if (type == null) {
            throw new IllegalArgumentException("type 은 필수입니다");
        }
        validateText(title, "title", TITLE_MAX_LENGTH);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 는 필수입니다");
        }
        validateImageUrl(imageUrl);
        validatePeriod(startsAt, endsAt);

        Notice n = new Notice();
        n.adminId = adminId;
        n.type = type;
        n.title = title.trim();
        n.content = content;  
        n.imageUrl = imageUrl;
        n.startsAt = startsAt;
        n.endsAt = endsAt;
        n.pinned = false;
        n.published = true;  
        n.viewCount = 0;
        return n;
    }

    public void update(
            NoticeType type,
            String title,
            String content,
            String imageUrl,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        if (type == null) {
            throw new IllegalArgumentException("type 은 필수입니다");
        }
        validateText(title, "title", TITLE_MAX_LENGTH);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content 는 필수입니다");
        }
        validateImageUrl(imageUrl);
        validatePeriod(startsAt, endsAt);
        this.type = type;
        this.title = title.trim();
        this.content = content;
        this.imageUrl = imageUrl;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void pin() {
        this.pinned = true;
    }

    public void unpin() {
        this.pinned = false;
    }

    public void publish() {
        this.published = true;
    }

    public void unpublish() {
        this.published = false;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    

    public boolean isVisibleAt(LocalDateTime now) {
        if (!published) {
            return false;
        }
        if (now == null) {
            return false;
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return false;
        }
        
        if (endsAt != null && !now.isBefore(endsAt)) {
            return false;
        }
        return true;
    }

    private static void validateText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 은 필수입니다");
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " 은 " + maxLength + "자 이하여야 합니다");
        }
    }

    private static void validateImageUrl(String imageUrl) {
        if (imageUrl != null && imageUrl.length() > IMAGE_URL_MAX_LENGTH) {
            throw new IllegalArgumentException("imageUrl 은 " + IMAGE_URL_MAX_LENGTH + "자 이하여야 합니다");
        }
    }

    private static void validatePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt 은 endsAt 보다 이전이어야 합니다");
        }
    }
}
