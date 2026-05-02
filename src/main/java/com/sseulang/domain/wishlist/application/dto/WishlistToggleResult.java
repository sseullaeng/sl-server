package com.sseulang.domain.wishlist.application.dto;

/**
 * 찜 추가/해제 직후 응답 — 프론트가 detail 재조회 없이 카드 UI 즉시 갱신할 수 있도록.
 *
 * @param wishlisted 호출 후 viewer 가 해당 item 을 찜하고 있는 상태인지
 * @param wishlistCount 호출 후 item 의 누적 찜 수 (fresh DB 값)
 */
public record WishlistToggleResult(boolean wishlisted, int wishlistCount) { }
