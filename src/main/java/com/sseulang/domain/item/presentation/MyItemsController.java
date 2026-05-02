package com.sseulang.domain.item.presentation;

import com.sseulang.domain.item.application.ItemApplicationService;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.presentation.dto.ItemSummaryResponse;
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
 * 마이페이지용 본인 등록 물품 목록. {@link ItemController} 의 공개 검색과 분리 — 본인만 접근.
 *
 * <p>{@code status} 쿼리로 탭 분리 가능: 판매중 / 예약 / 거래완료 / 비공개. 미지정 시 삭제만 자동 제외.</p>
 */
@Tag(name = "Item", description = "마이페이지 본인 물품 목록")
@RestController
@RequestMapping("/api/v1/users/me/items")
public class MyItemsController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ItemApplicationService itemService;

    public MyItemsController(ItemApplicationService itemService) {
        this.itemService = itemService;
    }

    @Operation(summary = "내 물품 목록",
            description = "본인이 등록한 물품 페이징. status 미지정 시 삭제 외 전체 (판매중/예약/거래완료/비공개). "
                    + "잘못된 status 값은 400 으로 반환됨.")
    @GetMapping
    public ApiResponse<PageResponse<ItemSummaryResponse>> listMine(
            @AuthenticationPrincipal Long sellerId,
            @RequestParam(name = "status", required = false) ItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<ItemSummaryResponse> result = itemService.findMyItems(sellerId, status, pageable)
                .map(ItemSummaryResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }
}
