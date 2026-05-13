package com.sseulang.domain.review.infrastructure.persistence;

import com.sseulang.domain.review.domain.Review;
import com.sseulang.domain.review.domain.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewRepositoryImpl implements ReviewRepository {

    private final ReviewJpaRepository jpa;

    public ReviewRepositoryImpl(ReviewJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Review save(Review review) {
        return jpa.save(review);
    }

    @Override
    public Page<Review> findByRevieweeId(Long revieweeId, Pageable pageable) {
        return jpa.findByRevieweeIdOrderByCreatedAtDesc(revieweeId, pageable);
    }

    @Override
    public java.util.Optional<Review> findById(Long id) {
        return jpa.findById(id);
    }
}
