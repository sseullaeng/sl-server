package com.sseulang.domain.banner.domain;

import com.sseulang.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Banner Aggregate Root. V1 스키마 {@code banners} 매핑.
 *
 * <p>배너는 {@code is_active} + 게시 윈도우 (startsAt/endsAt) 두 축으로 노출 결정.
 * sort_order 가 작을수록 먼저 노출.</p>
 */
@Entity
@Table(name = "banners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner extends BaseEntity {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int URL_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "image_url", nullable = false, length = URL_MAX_LENGTH)
    private String imageUrl;

    @Column(name = "link_url", length = URL_MAX_LENGTH)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    public static Banner create(
            Long adminId,
            String title,
            String imageUrl,
            String linkUrl,
            int sortOrder,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        validateText(title, "title", TITLE_MAX_LENGTH);
        validateText(imageUrl, "imageUrl", URL_MAX_LENGTH);
        validateOptional(linkUrl, "linkUrl", URL_MAX_LENGTH);
        validatePeriod(startsAt, endsAt);

        Banner b = new Banner();
        b.adminId = adminId;
        b.title = title.trim();
        b.imageUrl = imageUrl.trim();
        b.linkUrl = linkUrl == null ? null : linkUrl.trim();
        b.sortOrder = sortOrder;
        b.active = true;
        b.startsAt = startsAt;
        b.endsAt = endsAt;
        return b;
    }

    public void update(
            String title,
            String imageUrl,
            String linkUrl,
            int sortOrder,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        validateText(title, "title", TITLE_MAX_LENGTH);
        validateText(imageUrl, "imageUrl", URL_MAX_LENGTH);
        validateOptional(linkUrl, "linkUrl", URL_MAX_LENGTH);
        validatePeriod(startsAt, endsAt);
        this.title = title.trim();
        this.imageUrl = imageUrl.trim();
        this.linkUrl = linkUrl == null ? null : linkUrl.trim();
        this.sortOrder = sortOrder;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    /** 사용자 노출 — active + 게시 윈도우 안. */
    public boolean isVisibleAt(LocalDateTime now) {
        if (!active || now == null) {
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

    private static void validateOptional(String value, String name, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(name + " 은 " + maxLength + "자 이하여야 합니다");
        }
    }

    private static void validatePeriod(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt 은 endsAt 보다 이전이어야 합니다");
        }
    }
}
