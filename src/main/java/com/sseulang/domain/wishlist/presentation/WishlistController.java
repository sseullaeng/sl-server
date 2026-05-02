package com.sseulang.domain.wishlist.presentation;

import com.sseulang.domain.wishlist.application.WishlistApplicationService;
import com.sseulang.domain.wishlist.presentation.dto.WishlistToggleResponse;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wishlist 진입점은 Item 자원 하위로 둔다 (가이드 §6.1):
 * {@code POST /api/v1/items/{id}/wishlist}, {@code DELETE /api/v1/items/{id}/wishlist}.
 *
 * <p>응답에 fresh {@code wishlisted + wishlistCount} 동봉 — 프론트가 detail 재조회 없이 즉시 UI 갱신.</p>
 */
@Tag(name = "Wishlist", description = "물품 찜 추가/해제")
@RestController
@RequestMapping("/api/v1/items/{itemId}/wishlist")
public class WishlistController {

    private final WishlistApplicationService wishlistService;

    public WishlistController(WishlistApplicationService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @Operation(summary = "찜 추가", description = "멱등 — 이미 찜한 물품 재호출 OK. 응답에 fresh wishlistCount.")
    @PostMapping
    public ApiResponse<WishlistToggleResponse> add(
            @AuthenticationPrincipal Long userId,
            @PathVariable("itemId") Long itemId
    ) {
        return ApiResponse.ok(WishlistToggleResponse.from(wishlistService.add(userId, itemId)));
    }

    @Operation(summary = "찜 해제", description = "멱등 — 찜 안 한 상태 재호출 OK. wishlist_count 자동 감소(음수 방지). 응답에 fresh wishlistCount.")
    @DeleteMapping
    public ApiResponse<WishlistToggleResponse> remove(
            @AuthenticationPrincipal Long userId,
            @PathVariable("itemId") Long itemId
    ) {
        return ApiResponse.ok(WishlistToggleResponse.from(wishlistService.remove(userId, itemId)));
    }
}
