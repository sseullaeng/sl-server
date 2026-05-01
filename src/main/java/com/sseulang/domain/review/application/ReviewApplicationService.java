package com.sseulang.domain.review.application;

import com.sseulang.domain.review.application.dto.ReviewResult;
import com.sseulang.domain.review.application.dto.ReviewWriteCommand;
import com.sseulang.domain.review.domain.Review;
import com.sseulang.domain.review.domain.ReviewRepository;
import com.sseulang.domain.transaction.application.TransactionApplicationService;
import com.sseulang.domain.transaction.application.dto.PendingReviewableResult;
import com.sseulang.domain.transaction.application.dto.ReviewableTransactionResult;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewApplicationService {

    /** V1 스키마 {@code uk_reviews_tx_reviewer} — 동일 reviewer 같은 거래 두 번 작성 시 race 보정. */
    private static final String UNIQUE_TX_REVIEWER = "uk_reviews_tx_reviewer";

    private final ReviewRepository reviewRepository;
    private final TransactionApplicationService transactionApplicationService;
    private final UserApplicationService userApplicationService;

    public ReviewApplicationService(
            ReviewRepository reviewRepository,
            TransactionApplicationService transactionApplicationService,
            UserApplicationService userApplicationService
    ) {
        this.reviewRepository = reviewRepository;
        this.transactionApplicationService = transactionApplicationService;
        this.userApplicationService = userApplicationService;
    }

    /**
     * Review 작성. Transaction 검증(거래완료 + 7일 + 참여자) 은 TransactionApplicationService 가 수행.
     * UNIQUE(tx, reviewer) race 는 좁은 catch → REVIEW_DUPLICATED. 작성 후 reviewee 의 trust_score atomic 갱신.
     */
    @Transactional
    public Long write(ReviewWriteCommand cmd) {
        ReviewableTransactionResult info = transactionApplicationService
                .findCompletedForReview(cmd.transactionId(), cmd.reviewerId());

        Review review = Review.create(
                info.transactionId(),
                info.reviewerId(),
                info.revieweeId(),
                cmd.rating(),
                cmd.comment()
        );

        Long savedId;
        try {
            savedId = reviewRepository.save(review).getId();
        } catch (DataIntegrityViolationException violation) {
            if (isUniqueConflict(violation)) {
                throw new BusinessException(ErrorCode.REVIEW_DUPLICATED);
            }
            throw violation;
        }

        // 가이드 §4.7 — 작성 시점 review_count / rating_sum 누적 + trust_score atomic 재계산.
        // 단일 UPDATE 라 race 안전 (Codex 게이트 2 보강).
        userApplicationService.recordReview(info.revieweeId(), cmd.rating());

        return savedId;
    }

    /**
     * 받은 리뷰 페이징. 한줄평은 작성자 본인만 보이도록 마스킹 (가이드 §4.7 "한줄평은 본인만").
     */
    public Page<ReviewResult> listReceived(Long revieweeId, Long requesterId, Pageable pageable) {
        return reviewRepository.findByRevieweeId(revieweeId, pageable)
                .map(ReviewResult::from)
                .map(r -> r.masked(requesterId));
    }

    /**
     * Review 작성 대기 거래 목록 (follow-up #56). 본인이 reviewer 로 아직 작성 안 한 7일 이내 완료 거래.
     * Cross-aggregate read 는 TransactionApplicationService 가 책임 — 본 서비스는 단순 위임.
     */
    public Page<PendingReviewableResult> listPending(Long requesterId, Pageable pageable) {
        return transactionApplicationService.findPendingReviewable(requesterId, pageable);
    }

    private static boolean isUniqueConflict(DataIntegrityViolationException violation) {
        Throwable cause = violation;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException cve
                    && UNIQUE_TX_REVIEWER.equalsIgnoreCase(cve.getConstraintName())) {
                return true;
            }
            Throwable next = cause.getCause();
            if (next == cause) return false;
            cause = next;
        }
        return false;
    }
}
