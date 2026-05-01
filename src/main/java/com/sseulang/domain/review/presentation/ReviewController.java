package com.sseulang.domain.review.presentation;

import com.sseulang.domain.review.application.ReviewApplicationService;
import com.sseulang.domain.review.presentation.dto.PendingReviewResponse;
import com.sseulang.domain.review.presentation.dto.ReviewIdResponse;
import com.sseulang.domain.review.presentation.dto.ReviewResponse;
import com.sseulang.domain.review.presentation.dto.ReviewWriteRequest;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewApplicationService reviewService;

    public ReviewController(ReviewApplicationService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/v1/reviews")
    public ResponseEntity<ApiResponse<ReviewIdResponse>> write(
            @AuthenticationPrincipal Long reviewerId,
            @Valid @RequestBody ReviewWriteRequest request
    ) {
        Long id = reviewService.write(request.toCommand(reviewerId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new ReviewIdResponse(id)));
    }

    @GetMapping("/api/v1/users/{userId}/reviews")
    public ApiResponse<PageResponse<ReviewResponse>> listReceived(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("userId") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<ReviewResponse> result = reviewService.listReceived(userId, requesterId, pageable)
                .map(ReviewResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }

    /**
     * Review 작성 대기 거래 (follow-up #56). 본인이 reviewer 로 아직 작성 안 한 7일 이내 완료 거래.
     * 인증 필수 — SecurityFilter 가 보호.
     */
    @GetMapping("/api/v1/reviews/pending")
    public ApiResponse<PageResponse<PendingReviewResponse>> listPending(
            @AuthenticationPrincipal Long requesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<PendingReviewResponse> result = reviewService.listPending(requesterId, pageable)
                .map(PendingReviewResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }
}
