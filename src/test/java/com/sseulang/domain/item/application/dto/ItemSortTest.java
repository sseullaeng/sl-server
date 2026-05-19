package com.sseulang.domain.item.application.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemSortTest {

    @Test
    @DisplayName("parseList null/blank → [LATEST]")
    void parseList_null() {
        assertThat(ItemSort.parseList(null)).containsExactly(ItemSort.LATEST);
        assertThat(ItemSort.parseList("")).containsExactly(ItemSort.LATEST);
        assertThat(ItemSort.parseList("   ")).containsExactly(ItemSort.LATEST);
    }

    @Test
    @DisplayName("parseList 단일 → [LATEST]")
    void parseList_single() {
        assertThat(ItemSort.parseList("wishlist_desc")).containsExactly(ItemSort.WISHLIST_DESC);
    }

    @Test
    @DisplayName("parseList CSV → 순서 보존 + 중복 제거")
    void parseList_csv_dedup() {
        List<ItemSort> r = ItemSort.parseList("wishlist_desc,view_desc,latest,wishlist_desc");
        assertThat(r).containsExactly(ItemSort.WISHLIST_DESC, ItemSort.VIEW_DESC, ItemSort.LATEST);
    }

    @Test
    @DisplayName("parseList 무효 토큰 무시")
    void parseList_invalid_skipped() {
        List<ItemSort> r = ItemSort.parseList("foo,view_desc,bar");
        assertThat(r).containsExactly(ItemSort.VIEW_DESC);
    }

    @Test
    @DisplayName("parseList 모두 무효 → [LATEST]")
    void parseList_all_invalid() {
        assertThat(ItemSort.parseList("foo,bar")).containsExactly(ItemSort.LATEST);
    }
}
