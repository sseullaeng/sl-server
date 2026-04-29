package com.sseulang.domain.review.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewTest {

    @Test
    @DisplayName("create 정상")
    void create_정상() {
        Review r = Review.create(10L, 100L, 200L, 5, "친절했어요");
        assertThat(r.getTransactionId()).isEqualTo(10L);
        assertThat(r.getReviewerId()).isEqualTo(100L);
        assertThat(r.getRevieweeId()).isEqualTo(200L);
        assertThat(r.getRating()).isEqualTo(5);
        assertThat(r.getComment()).isEqualTo("친절했어요");
    }

    @Test
    @DisplayName("create rating 0/6_거부")
    void create_rating_범위_거부() {
        assertThatThrownBy(() -> Review.create(10L, 100L, 200L, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Review.create(10L, 100L, 200L, 6, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Review.create(10L, 100L, 200L, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create reviewer == reviewee_거부")
    void create_self_거부() {
        assertThatThrownBy(() -> Review.create(10L, 100L, 100L, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create comment 500자 초과_거부")
    void create_comment_길이초과_거부() {
        String tooLong = "가".repeat(501);
        assertThatThrownBy(() -> Review.create(10L, 100L, 200L, 5, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create null/비양수 ID_거부")
    void create_id_거부() {
        assertThatThrownBy(() -> Review.create(null, 100L, 200L, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Review.create(10L, 0L, 200L, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Review.create(10L, 100L, -1L, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
