package com.sseulang.domain.notice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoticeTest {

    private static final Long ADMIN = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 30, 12, 0);

    private Notice newNotice() {
        return Notice.create(ADMIN, NoticeType.공지, "제목", "본문", null, null, null);
    }

    @Test
    @DisplayName("create 정상_published=true / pinned=false / viewCount=0 default")
    void create_정상_default() {
        Notice n = newNotice();

        assertThat(n.getAdminId()).isEqualTo(ADMIN);
        assertThat(n.getType()).isEqualTo(NoticeType.공지);
        assertThat(n.getTitle()).isEqualTo("제목");
        assertThat(n.getContent()).isEqualTo("본문");
        assertThat(n.isPinned()).isFalse();
        assertThat(n.isPublished()).isTrue();
        assertThat(n.getViewCount()).isZero();
    }

    @Test
    @DisplayName("create_invalid 인자 거부")
    void create_invalid() {
        assertThatThrownBy(() -> Notice.create(ADMIN, null, "t", "c", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notice.create(ADMIN, NoticeType.공지, "", "c", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notice.create(ADMIN, NoticeType.공지, "t", " ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notice.create(ADMIN, NoticeType.공지, "t", "c", null, NOW, NOW.minusDays(1)))
                .as("startsAt > endsAt")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Notice.create(ADMIN, NoticeType.공지, "t", "c", null, NOW, NOW))
                .as("startsAt == endsAt")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("pin/unpin/publish/unpublish 토글")
    void state_toggle() {
        Notice n = newNotice();
        n.pin();
        assertThat(n.isPinned()).isTrue();
        n.unpin();
        assertThat(n.isPinned()).isFalse();

        n.unpublish();
        assertThat(n.isPublished()).isFalse();
        n.publish();
        assertThat(n.isPublished()).isTrue();
    }

    @Test
    @DisplayName("incrementViewCount_누적 증가")
    void incrementViewCount_누적() {
        Notice n = newNotice();
        n.incrementViewCount();
        n.incrementViewCount();
        assertThat(n.getViewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("isVisibleAt_unpublished 면 false")
    void isVisibleAt_unpublished() {
        Notice n = newNotice();
        n.unpublish();
        assertThat(n.isVisibleAt(NOW)).isFalse();
    }

    @Test
    @DisplayName("isVisibleAt_window 검증 — startsAt 이전이면 false, endsAt 도달 시 false")
    void isVisibleAt_window() {
        Notice n = Notice.create(ADMIN, NoticeType.이벤트, "t", "c", null,
                NOW, NOW.plusDays(7));

        assertThat(n.isVisibleAt(NOW.minusMinutes(1))).as("시작 전").isFalse();
        assertThat(n.isVisibleAt(NOW)).as("시작 시점 포함").isTrue();
        assertThat(n.isVisibleAt(NOW.plusDays(3))).as("기간 중").isTrue();
        assertThat(n.isVisibleAt(NOW.plusDays(7))).as("종료 시점은 노출 X (exclusive)").isFalse();
        assertThat(n.isVisibleAt(NOW.plusDays(8))).as("종료 후").isFalse();
    }

    @Test
    @DisplayName("isVisibleAt_window null = 무제한")
    void isVisibleAt_no_window() {
        Notice n = newNotice();
        assertThat(n.isVisibleAt(NOW)).isTrue();
        assertThat(n.isVisibleAt(NOW.plusYears(10))).isTrue();
    }

    @Test
    @DisplayName("update_필드 변경 + 검증")
    void update_정상() {
        Notice n = newNotice();
        n.update(NoticeType.이벤트, "새 제목", "새 본문", "https://i/u.png", NOW, NOW.plusDays(3));

        assertThat(n.getType()).isEqualTo(NoticeType.이벤트);
        assertThat(n.getTitle()).isEqualTo("새 제목");
        assertThat(n.getContent()).isEqualTo("새 본문");
        assertThat(n.getImageUrl()).isEqualTo("https://i/u.png");
        assertThat(n.getStartsAt()).isEqualTo(NOW);
        assertThat(n.getEndsAt()).isEqualTo(NOW.plusDays(3));
    }
}
