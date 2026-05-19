package com.sseulang.domain.category.domain;

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

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public static Category createRoot(String name, int sortOrder) {
        return create(null, name, sortOrder);
    }

    public static Category createChild(Long parentId, String name, int sortOrder) {
        if (parentId == null || parentId <= 0) {
            throw new IllegalArgumentException("parentId 는 양수여야 합니다");
        }
        return create(parentId, name, sortOrder);
    }

    public boolean isRoot() {
        return parentId == null;
    }

    private static Category create(Long parentId, String name, int sortOrder) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 은 비어있을 수 없습니다");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("name 은 " + NAME_MAX_LENGTH + "자를 초과할 수 없습니다");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder 는 0 이상이어야 합니다");
        }
        Category c = new Category();
        c.parentId = parentId;
        c.name = name;
        c.sortOrder = sortOrder;
        c.active = true;
        return c;
    }
}
