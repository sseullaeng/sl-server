package com.sseulang.domain.review.presentation;

import com.sseulang.domain.review.application.ReviewApplicationService;
import com.sseulang.domain.review.presentation.dto.PendingReviewResponse;
import com.sseulang.domain.review.presentation.dto.ReviewIdResponse;
import com.sseulang.domain.review.presentation.dto.ReviewResponse;
import com.sseulang.domain.review.presentation.dto.ReviewVisibilityRequest;
import com.sseulang.domain.review.presentation.dto.ReviewWriteRequest;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Review", description = "거래 리뷰 작성/조회/대기 목록")
@RestController
public class ReviewController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewApplicationService reviewService;

    public ReviewController(ReviewApplicationService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "리뷰 작성",
            description = "거래완료 후 7일 이내 + 거래 참여자만. 같은 거래 본인 리뷰 중복 시 409 REVIEW_DUPLICATED. "
                    + "작성 후 reviewee 의 trust_score 자동 재계산 (atomic UPDATE).")
    @PostMapping("/api/v1/reviews")
    public ResponseEntity<ApiResponse<ReviewIdResponse>> write(
            @AuthenticationPrincipal Long reviewerId,
            @Valid @RequestBody ReviewWriteRequest request
    ) {
        Long id = reviewService.write(request.toCommand(reviewerId));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(new ReviewIdResponse(id)));
    }

    @Operation(summary = "사용자가 받은 리뷰 목록",
            description = "userId 가 reviewee 인 리뷰 페이징. 한줄평(comment)은 작성자 본인에게만 노출, 그 외엔 null 마스킹.")
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

    @Operation(summary = "리뷰 한줄평 공개여부 토글",
            description = "리뷰 대상자(reviewee) 만 호출 가능. 별점은 항상 공개 — 한줄평(comment) 만 마스킹. "
                    + "false 시 제3자 응답에서 comment=null. 본인은 자기 페이지에서 항상 원본 + flag 함께 받음.")
    @PatchMapping("/api/v1/reviews/{id}/visibility")
    public ApiResponse<ReviewResponse> setVisibility(
            @AuthenticationPrincipal Long requesterId,
            @PathVariable("id") Long reviewId,
            @Valid @RequestBody ReviewVisibilityRequest request
    ) {
        return ApiResponse.ok(ReviewResponse.from(
                reviewService.setVisibility(reviewId, requesterId, request.contentVisible())
        ));
    }

    @Operation(summary = "리뷰 작성 대기 목록",
            description = "본인이 reviewer 로 아직 작성 안 한 7일 이내 완료 거래. deadline 필드로 남은 시간 카운트다운 UI 가능.")
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
