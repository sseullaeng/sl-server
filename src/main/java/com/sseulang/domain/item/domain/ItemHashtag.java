package com.sseulang.domain.item.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "item_hashtags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItemHashtag {

    static final int TAG_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "tag", nullable = false, length = TAG_MAX_LENGTH)
    private String tag;

    
    ItemHashtag(Item item, String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("tag 는 비어있을 수 없습니다");
        }
        String normalized = tag.strip();
        if (normalized.length() > TAG_MAX_LENGTH) {
            throw new IllegalArgumentException("tag 는 " + TAG_MAX_LENGTH + "자를 초과할 수 없습니다");
        }
        this.item = item;
        this.tag = normalized;
    }
}
