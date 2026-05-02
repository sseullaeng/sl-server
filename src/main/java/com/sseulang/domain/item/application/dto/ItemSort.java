package com.sseulang.domain.item.application.dto;

/**
 * Item 검색 정렬 옵션. URL query param `sort` 의 값과 1:1 매핑.
 * 잘못된 값은 controller 에서 LATEST 로 fallback.
 */
public enum ItemSort {
    /** createdAt DESC (default). */
    LATEST,
    /** price ASC. */
    PRICE_ASC,
    /** price DESC. */
    PRICE_DESC,
    /** viewCount DESC — 조회순. 동률은 createdAt DESC. */
    VIEW_DESC,
    /** wishlistCount DESC — 찜많은순. 동률은 createdAt DESC. */
    WISHLIST_DESC;

    public static ItemSort parse(String raw) {
        if (raw == null || raw.isBlank()) return LATEST;
        try {
            return ItemSort.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LATEST;
        }
    }
}
