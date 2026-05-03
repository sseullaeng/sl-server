package com.sseulang.domain.support.application;

import com.sseulang.domain.support.domain.InquiryCategory;
import com.sseulang.domain.support.domain.SupportPost;
import com.sseulang.domain.support.domain.SupportPostRepository;
import com.sseulang.domain.support.domain.SupportPostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class InMemoryFakeSupportPostRepository implements SupportPostRepository {

    private final Map<Long, SupportPost> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public SupportPost save(SupportPost post) {
        if (post.getId() == null) {
            ReflectionTestUtils.setField(post, "id", ++sequence);
        }
        store.put(post.getId(), post);
        return post;
    }

    @Override
    public Optional<SupportPost> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public Page<SupportPost> findVisible(SupportPostType type, InquiryCategory category, Pageable pageable) {
        List<SupportPost> filtered = store.values().stream()
                .filter(p -> p.getPostType() == type)
                .filter(p -> category == null || p.getCategory() == category)
                .sorted(Comparator.comparing(SupportPost::getId).reversed())
                .toList();
        return new PageImpl<>(filtered, pageable, filtered.size());
    }

    int size() {
        return store.size();
    }
}
