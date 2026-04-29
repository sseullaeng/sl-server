package com.sseulang.domain.review.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewRepository {

    Review save(Review review);

    /** 특정 사용자가 받은 리뷰 페이징 (최신순). */
    Page<Review> findByRevieweeId(Long revieweeId, Pageable pageable);
}
