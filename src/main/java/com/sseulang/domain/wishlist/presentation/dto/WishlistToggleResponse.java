package com.sseulang.domain.wishlist.presentation.dto;

import com.sseulang.domain.wishlist.application.dto.WishlistToggleResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "찜 추가/해제 직후 응답 — 프론트가 즉시 카드 UI 갱신할 수 있도록 wishlisted + 누적 wishlistCount 동봉.")
public record WishlistToggleResponse(
        @Schema(example = "true", description = "호출 후 찜 상태")
        boolean wishlisted,
        @Schema(example = "9", description = "호출 후 해당 item 의 누적 wishlistCount")
        int wishlistCount
) {
    public static WishlistToggleResponse from(WishlistToggleResult r) {
        return new WishlistToggleResponse(r.wishlisted(), r.wishlistCount());
    }
}
