package com.sseulang.domain.category.application;

import com.sseulang.domain.category.domain.Category;
import com.sseulang.domain.category.domain.CategoryRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 테스트용 인메모리 fake. {@code findAllActiveSorted} 가 prod 와 동일한 정렬을 흉내 내야
 * ApplicationService 의 1-pass 트리 조립이 정상 동작한다 — parent null 먼저, parentId asc, sortOrder asc.
 */
public class InMemoryFakeCategoryRepository implements CategoryRepository {

    private final Map<Long, Category> store = new HashMap<>();
    private long sequence = 0;

    public Category insert(Category c) {
        long id = ++sequence;
        ReflectionTestUtils.setField(c, "id", id);
        store.put(id, c);
        return c;
    }

    @Override
    public Optional<Category> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Category> findAllActiveSorted() {
        List<Category> active = new ArrayList<>();
        for (Category c : store.values()) {
            if (c.isActive()) active.add(c);
        }
        active.sort(Comparator
                .comparing((Category c) -> c.getParentId() == null ? 0 : 1)
                .thenComparing(c -> c.getParentId() == null ? Long.MIN_VALUE : c.getParentId())
                .thenComparingInt(Category::getSortOrder)
                .thenComparing(Category::getId));
        return active;
    }

    @Override
    public List<Category> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        String lower = keyword.toLowerCase();
        List<Category> result = new ArrayList<>();
        for (Category c : store.values()) {
            if (c.isActive() && c.getName() != null && c.getName().toLowerCase().contains(lower)) {
                result.add(c);
            }
        }
        result.sort(Comparator.comparingInt(Category::getSortOrder).thenComparing(Category::getId));
        return result;
    }
}
