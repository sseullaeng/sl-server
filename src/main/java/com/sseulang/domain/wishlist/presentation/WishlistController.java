package com.sseulang.domain.wishlist.presentation;

import com.sseulang.domain.wishlist.application.WishlistApplicationService;
import com.sseulang.global.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wishlist 진입점은 Item 자원 하위로 둔다 (가이드 §6.1):
 * {@code POST /api/v1/items/{id}/wishlist}, {@code DELETE /api/v1/items/{id}/wishlist}.
 */
@RestController
@RequestMapping("/api/v1/items/{itemId}/wishlist")
public class WishlistController {

    private final WishlistApplicationService wishlistService;

    public WishlistController(WishlistApplicationService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @PostMapping
    public ApiResponse<Void> add(
            @AuthenticationPrincipal Long userId,
            @PathVariable("itemId") Long itemId
    ) {
        wishlistService.add(userId, itemId);
        return ApiResponse.ok();
    }

    @DeleteMapping
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal Long userId,
            @PathVariable("itemId") Long itemId
    ) {
        wishlistService.remove(userId, itemId);
        return ApiResponse.ok();
    }
}
