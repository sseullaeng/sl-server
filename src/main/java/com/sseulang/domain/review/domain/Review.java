package com.sseulang.domain.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    private static final int COMMENT_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    @Column(name = "reviewee_id", nullable = false)
    private Long revieweeId;

    
    @Column(name = "rating", nullable = false)
    @JdbcTypeCode(SqlTypes.TINYINT)
    private int rating;

    @Column(name = "comment", length = COMMENT_MAX_LENGTH)
    private String comment;

    @Column(name = "content_visible", nullable = false)
    private boolean contentVisible = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static Review create(Long transactionId, Long reviewerId, Long revieweeId, int rating, String comment) {
        if (transactionId == null || transactionId <= 0) {
            throw new IllegalArgumentException("transactionId 는 양수여야 합니다");
        }
        if (reviewerId == null || reviewerId <= 0) {
            throw new IllegalArgumentException("reviewerId 는 양수여야 합니다");
        }
        if (revieweeId == null || revieweeId <= 0) {
            throw new IllegalArgumentException("revieweeId 는 양수여야 합니다");
        }
        if (reviewerId.equals(revieweeId)) {
            throw new IllegalArgumentException("자기 자신에게는 리뷰를 작성할 수 없습니다");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating 은 1~5 범위여야 합니다");
        }
        if (comment != null && comment.length() > COMMENT_MAX_LENGTH) {
            throw new IllegalArgumentException("comment 는 " + COMMENT_MAX_LENGTH + "자를 초과할 수 없습니다");
        }
        Review r = new Review();
        r.transactionId = transactionId;
        r.reviewerId = reviewerId;
        r.revieweeId = revieweeId;
        r.rating = rating;
        r.comment = comment;
        r.contentVisible = true;
        return r;
    }

    // 대상자(reviewee) 만 토글 가능. 그 외 호출 시 예외 — service 단에서 가드.
    public void setContentVisible(Long requesterId, boolean visible) {
        if (requesterId == null || !requesterId.equals(this.revieweeId)) {
            throw new IllegalStateException("리뷰 대상자만 공개여부를 변경할 수 있습니다");
        }
        this.contentVisible = visible;
    }
}
