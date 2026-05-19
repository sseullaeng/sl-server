package com.sseulang.domain.review.application;

import com.sseulang.domain.review.domain.Review;
import com.sseulang.domain.review.domain.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemoryFakeReviewRepository implements ReviewRepository {

    private final Map<Long, Review> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Review save(Review review) {
        if (review.getId() == null) {
            ReflectionTestUtils.setField(review, "id", ++sequence);
            ReflectionTestUtils.setField(review, "createdAt", LocalDateTime.now());
        }
        store.put(review.getId(), review);
        return review;
    }

    @Override
    public Page<Review> findByRevieweeId(Long revieweeId, Pageable pageable) {
        List<Review> filtered = store.values().stream()
                .filter(r -> r.getRevieweeId().equals(revieweeId))
                .sorted(Comparator.comparing(Review::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Override
    public java.util.Optional<Review> findById(Long id) {
        return java.util.Optional.ofNullable(store.get(id));
    }
}
