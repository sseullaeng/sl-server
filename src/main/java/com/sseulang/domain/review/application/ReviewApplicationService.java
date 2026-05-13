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

        
        
        userApplicationService.recordReview(info.revieweeId(), cmd.rating());

        return savedId;
    }

    

    public Page<ReviewResult> listReceived(Long revieweeId, Long requesterId, Pageable pageable) {
        return reviewRepository.findByRevieweeId(revieweeId, pageable)
                .map(ReviewResult::from)
                .map(r -> r.masked(requesterId));
    }

    @Transactional
    public ReviewResult setVisibility(Long reviewId, Long requesterId, boolean contentVisible) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
        if (requesterId == null || !requesterId.equals(review.getRevieweeId())) {
            throw new BusinessException(ErrorCode.REVIEW_FORBIDDEN);
        }
        review.setContentVisible(requesterId, contentVisible);
        return ReviewResult.from(review);
    }

    

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
