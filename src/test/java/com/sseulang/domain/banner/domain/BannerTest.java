package com.sseulang.domain.banner.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BannerTest {

    private static final Long ADMIN = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 30, 12, 0);

    private Banner newBanner() {
        return Banner.create(ADMIN, "여름 세일", "https://i/u.png", "https://link", 0, null, null);
    }

    @Test
    @DisplayName("create 정상_active=true / sortOrder 보존")
    void create_정상() {
        Banner b = Banner.create(ADMIN, "B", "https://i", null, 5, null, null);
        assertThat(b.isActive()).isTrue();
        assertThat(b.getSortOrder()).isEqualTo(5);
        assertThat(b.getLinkUrl()).isNull();
    }

    @Test
    @DisplayName("create_invalid 인자 거부")
    void create_invalid() {
        assertThatThrownBy(() -> Banner.create(ADMIN, "", "https://i", null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Banner.create(ADMIN, "t", " ", null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Banner.create(ADMIN, "t", "https://i", null, 0, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("activate/deactivate 토글")
    void state_토글() {
        Banner b = newBanner();
        b.deactivate();
        assertThat(b.isActive()).isFalse();
        b.activate();
        assertThat(b.isActive()).isTrue();
    }

    @Test
    @DisplayName("isVisibleAt_inactive 면 false")
    void isVisibleAt_inactive() {
        Banner b = newBanner();
        b.deactivate();
        assertThat(b.isVisibleAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("isVisibleAt_window 검증")
    void isVisibleAt_window() {
        Banner b = Banner.create(ADMIN, "t", "https://i", null, 0, NOW, NOW.plusDays(7));

        assertThat(b.isVisibleAt(NOW.minusMinutes(1))).isFalse();
        assertThat(b.isVisibleAt(NOW)).isTrue();
        assertThat(b.isVisibleAt(NOW.plusDays(7))).isFalse();
    }

    @Test
    @DisplayName("update 필드 변경")
    void update_정상() {
        Banner b = newBanner();
        b.update("새 제목", "https://new", "https://newlink", 9, NOW, NOW.plusDays(3));

        assertThat(b.getTitle()).isEqualTo("새 제목");
        assertThat(b.getSortOrder()).isEqualTo(9);
        assertThat(b.getLinkUrl()).isEqualTo("https://newlink");
    }
}
