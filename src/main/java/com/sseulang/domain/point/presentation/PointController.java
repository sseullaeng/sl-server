package com.sseulang.domain.point.presentation;

import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.presentation.dto.PointHistoryResponse;
import com.sseulang.global.common.ApiResponse;
import com.sseulang.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 포인트 관련 endpoint. 잔액은 {@code GET /api/v1/users/me} 의 {@code pointBalance} 로 노출되므로
 * 별도 balance endpoint 는 두지 않음. 본 컨트롤러는 history 페이징만.
 */
@Tag(name = "Point", description = "본인 포인트 히스토리 페이징")
@RestController
@RequestMapping("/api/v1/users/me/point")
public class PointController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PointApplicationService pointService;

    public PointController(PointApplicationService pointService) {
        this.pointService = pointService;
    }

    @Operation(summary = "본인 포인트 히스토리 페이징",
            description = "잔액 변동 내역 (충전/결제/판매정산/출금/환불/배달결제/배달정산). type 미지정 시 전체. "
                    + "정렬: createdAt DESC. 잔액 자체는 GET /users/me 의 pointBalance 사용.")
    @GetMapping("/history")
    public ApiResponse<PageResponse<PointHistoryResponse>> getMyHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "type", required = false) PointHistoryType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<PointHistoryResponse> result = pointService.findMyHistory(userId, type, pageable)
                .map(PointHistoryResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }
}
