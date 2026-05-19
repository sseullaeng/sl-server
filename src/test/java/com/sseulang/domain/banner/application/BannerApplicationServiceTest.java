package com.sseulang.domain.banner.application;

import com.sseulang.domain.banner.application.dto.BannerResult;
import com.sseulang.domain.banner.application.dto.BannerUpsertCommand;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BannerApplicationServiceTest {

    private static final Long ADMIN = 7L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 30, 12, 0);

    private InMemoryFakeBannerRepository repo;
    private BannerApplicationService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFakeBannerRepository();
        Clock clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        service = new BannerApplicationService(repo, clock);
    }

    private BannerUpsertCommand cmd(int sortOrder) {
        return new BannerUpsertCommand("배너 " + sortOrder, "https://i/" + sortOrder, null, sortOrder, null, null);
    }

    @Test
    @DisplayName("create 정상_active=true 기본")
    void create_정상() {
        Long id = service.create(ADMIN, cmd(1));
        assertThat(service.adminFindById(id).active()).isTrue();
    }

    @Test
    @DisplayName("setActive 토글")
    void setActive_토글() {
        Long id = service.create(ADMIN, cmd(1));
        service.setActive(id, false);
        assertThat(service.adminFindById(id).active()).isFalse();
    }

    @Test
    @DisplayName("delete 미존재_BANNER_NOT_FOUND")
    void delete_미존재_404() {
        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BANNER_NOT_FOUND);
    }

    @Test
    @DisplayName("findVisible_active 만 + sortOrder 정렬")
    void findVisible_정렬() {
        Long b1 = service.create(ADMIN, cmd(2));
        Long b2 = service.create(ADMIN, cmd(0));
        Long b3 = service.create(ADMIN, cmd(1));
        Long inactive = service.create(ADMIN, cmd(99));
        service.setActive(inactive, false);

        List<BannerResult> visible = service.findVisible();

        assertThat(visible).extracting(BannerResult::id)
                .as("sortOrder ASC, inactive 제외")
                .containsExactly(b2, b3, b1);
    }

    @Test
    @DisplayName("findVisible_window 외 제외")
    void findVisible_window_외() {
        Long future = service.create(ADMIN, new BannerUpsertCommand(
                "future", "https://i", null, 0, NOW.plusDays(1), NOW.plusDays(7)
        ));
        Long current = service.create(ADMIN, cmd(1));

        List<BannerResult> visible = service.findVisible();
        assertThat(visible).extracting(BannerResult::id).containsExactly(current);
    }
}
