package com.sseulang.domain.item.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Item 자식 엔티티. 외부 직접 생성/조작 금지 — Aggregate Root({@link Item}) 의 메서드를 통해서만.
 *
 * <p>{@code item_images} 테이블엔 {@code created_at} 만 있고 {@code updated_at} 이 없으므로
 * {@link com.sseulang.global.common.BaseEntity} 상속 X.</p>
 */
@Entity
@Table(name = "item_images")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_thumbnail", nullable = false)
    private boolean thumbnail;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Aggregate Root 만 호출 — package-private. */
    ItemImage(Item item, String imageUrl, int sortOrder, boolean thumbnail) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("imageUrl 은 비어있을 수 없습니다");
        }
        this.item = item;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
        this.thumbnail = thumbnail;
    }
}
