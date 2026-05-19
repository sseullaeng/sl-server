package com.sseulang.domain.item.application.dto;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public enum ItemSort {

    LATEST,

    PRICE_ASC,

    PRICE_DESC,

    VIEW_DESC,

    WISHLIST_DESC,

    // 거래완료(상태) 후순위. 같은 페이지 내에서 거래완료를 마지막으로 밀어 — page boundary 깨끗.
    COMPLETED_LAST;

    public static ItemSort parse(String raw) {
        if (raw == null || raw.isBlank()) return LATEST;
        try {
            return ItemSort.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LATEST;
        }
    }

    // CSV 입력(예: "wishlist_desc,view_desc,latest") → 중복 제거된 정렬 우선순위 리스트.
    // 빈 입력 / 모두 무효 → [LATEST] 단일.
    public static List<ItemSort> parseList(String raw) {
        if (raw == null || raw.isBlank()) return List.of(LATEST);
        LinkedHashSet<ItemSort> result = new LinkedHashSet<>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            try {
                result.add(ItemSort.valueOf(trimmed.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // 무시 — 무효 토큰
            }
        }
        if (result.isEmpty()) return List.of(LATEST);
        return Collections.unmodifiableList(Arrays.asList(result.toArray(new ItemSort[0])));
    }
}
