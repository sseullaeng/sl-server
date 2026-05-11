package com.sseulang.domain.point.presentation;

import com.sseulang.domain.point.application.PointApplicationService;
import com.sseulang.domain.point.domain.PointHistoryType;
import com.sseulang.domain.point.presentation.dto.PointBalanceResponse;
import com.sseulang.domain.point.presentation.dto.PointHistoryResponse;
import com.sseulang.domain.user.application.UserApplicationService;
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

@Tag(name = "Point", description = "본인 포인트 잔액 / 히스토리")
@RestController
@RequestMapping("/api/v1/users/me/point")
public class PointController {

    private static final int MAX_PAGE_SIZE = 100;

    private final PointApplicationService pointService;
    private final UserApplicationService userApplicationService;

    public PointController(
            PointApplicationService pointService,
            UserApplicationService userApplicationService
    ) {
        this.pointService = pointService;
        this.userApplicationService = userApplicationService;
    }

    @Operation(summary = "본인 포인트 잔액 (라운드 11)",
            description = "사용 가능(balance) / 거래 보관(holdAmount) / 합산(totalBalance) 3분할. "
                    + "두 컬럼을 단일 SELECT 로 읽어 동시 reserve/cancel/refund 사이의 합산 어긋남 방지 (게이트 1 W-1).")
    @GetMapping
    public ApiResponse<PointBalanceResponse> getMyBalance(@AuthenticationPrincipal Long userId) {
        var snapshot = userApplicationService.getPointSnapshot(userId);
        return ApiResponse.ok(PointBalanceResponse.of(snapshot.balance(), snapshot.hold()));
    }

    @Operation(summary = "본인 포인트 히스토리 페이징",
            description = "잔액 변동 내역 (충전/결제/판매정산/출금/환불/배달결제/배달정산/거래보관/거래환불). "
                    + "type 미지정 시 전체. 정렬: createdAt DESC. 잔액 자체는 GET /api/v1/users/me/point 또는 GET /users/me 사용.")
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
