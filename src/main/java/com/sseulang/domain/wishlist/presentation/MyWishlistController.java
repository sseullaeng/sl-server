package com.sseulang.domain.wishlist.presentation;

import com.sseulang.domain.item.presentation.dto.ItemSummaryResponse;
import com.sseulang.domain.wishlist.application.WishlistApplicationService;
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
 * 본인 찜 목록 조회 — {@link WishlistController} 의 add/remove 와 분리. path prefix 가 다름
 * ({@code /api/v1/users/me/wishlist}).
 */
@Tag(name = "Wishlist", description = "물품 찜 추가/해제 + 본인 목록")
@RestController
@RequestMapping("/api/v1/users/me/wishlist")
public class MyWishlistController {

    private static final int MAX_PAGE_SIZE = 100;

    private final WishlistApplicationService wishlistService;

    public MyWishlistController(WishlistApplicationService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @Operation(summary = "내 찜 목록",
            description = "본인이 찜한 활성 Item 페이징. 찜한 시간 최신순. 삭제된 Item 자동 제외.")
    @GetMapping
    public ApiResponse<PageResponse<ItemSummaryResponse>> listMine(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<ItemSummaryResponse> result = wishlistService.listMyWishlistedItems(userId, pageable)
                .map(ItemSummaryResponse::from);
        return ApiResponse.ok(PageResponse.from(result));
    }
}
