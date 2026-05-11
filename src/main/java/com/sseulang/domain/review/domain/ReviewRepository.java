package com.sseulang.domain.review.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewRepository {

    Review save(Review review);

    
    Page<Review> findByRevieweeId(Long revieweeId, Pageable pageable);
}
