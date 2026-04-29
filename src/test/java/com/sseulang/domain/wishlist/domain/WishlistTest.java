package com.sseulang.domain.wishlist.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WishlistTest {

    @Test
    @DisplayName("create 정상")
    void create_정상() {
        Wishlist w = Wishlist.create(1L, 100L);
        assertThat(w.getUserId()).isEqualTo(1L);
        assertThat(w.getItemId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("create null/비양수 userId_거부")
    void create_userId_거부() {
        assertThatThrownBy(() -> Wishlist.create(null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Wishlist.create(0L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Wishlist.create(-1L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("create null/비양수 itemId_거부")
    void create_itemId_거부() {
        assertThatThrownBy(() -> Wishlist.create(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Wishlist.create(1L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
