package com.sseulang.domain.notice.application;

import com.sseulang.domain.notice.application.dto.NoticeResult;
import com.sseulang.domain.notice.application.dto.NoticeUpsertCommand;
import com.sseulang.domain.notice.domain.NoticeType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoticeApplicationServiceTest {

    private static final Long ADMIN = 7L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 30, 12, 0);

    private InMemoryFakeNoticeRepository repo;
    private NoticeApplicationService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFakeNoticeRepository();
        clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST);
        service = new NoticeApplicationService(repo, clock);
    }

    private NoticeUpsertCommand cmd(NoticeType type) {
        return new NoticeUpsertCommand(type, "제목", "본문", null, null, null);
    }

    @Test
    @DisplayName("create 정상_id 반환 + 저장")
    void create_정상() {
        Long id = service.create(ADMIN, cmd(NoticeType.공지));

        assertThat(id).isNotNull();
        assertThat(repo.size()).isEqualTo(1);
        NoticeResult r = service.adminFindById(id);
        assertThat(r.adminId()).isEqualTo(ADMIN);
        assertThat(r.type()).isEqualTo(NoticeType.공지);
    }

    @Test
    @DisplayName("update 미존재_NOTICE_NOT_FOUND")
    void update_미존재_404() {
        assertThatThrownBy(() -> service.update(999L, cmd(NoticeType.공지)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    @DisplayName("setPinned/setPublished 토글")
    void state_토글() {
        Long id = service.create(ADMIN, cmd(NoticeType.공지));

        service.setPinned(id, true);
        assertThat(service.adminFindById(id).pinned()).isTrue();

        service.setPublished(id, false);
        assertThat(service.adminFindById(id).published()).isFalse();
    }

    @Test
    @DisplayName("delete 정상_저장소에서 사라짐")
    void delete_정상() {
        Long id = service.create(ADMIN, cmd(NoticeType.공지));
        service.delete(id);
        assertThat(repo.size()).isZero();
    }

    @Test
    @DisplayName("delete 미존재_NOTICE_NOT_FOUND")
    void delete_미존재_404() {
        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    @DisplayName("viewById 정상_view_count 1 증가 + Result 반환")
    void viewById_view_증가() {
        Long id = service.create(ADMIN, cmd(NoticeType.공지));
        service.viewById(id);
        service.viewById(id);

        assertThat(service.adminFindById(id).viewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("viewById 미공개_NOTICE_NOT_FOUND (존재 leak X)")
    void viewById_미공개_404() {
        Long id = service.create(ADMIN, cmd(NoticeType.공지));
        service.setPublished(id, false);

        assertThatThrownBy(() -> service.viewById(id))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    @DisplayName("viewById 윈도우 외_NOTICE_NOT_FOUND")
    void viewById_윈도우외_404() {
        // 시작이 미래 — 아직 노출되면 안 됨
        Long id = service.create(ADMIN, new NoticeUpsertCommand(
                NoticeType.이벤트, "t", "c", null, NOW.plusDays(1), NOW.plusDays(7)
        ));
        assertThatThrownBy(() -> service.viewById(id))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    @DisplayName("findVisible_pinned 우선 정렬 + 미공개/윈도우 외 제외")
    void findVisible_정렬() {
        Long pinnedId = service.create(ADMIN, cmd(NoticeType.공지));
        service.setPinned(pinnedId, true);
        Long normalId = service.create(ADMIN, cmd(NoticeType.공지));

        Long hiddenId = service.create(ADMIN, cmd(NoticeType.공지));
        service.setPublished(hiddenId, false);

        Page<NoticeResult> page = service.findVisible(null, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(NoticeResult::id)
                .as("pinned 가 먼저, hidden 은 누락")
                .containsExactly(pinnedId, normalId);
    }

    @Test
    @DisplayName("findVisible_type 필터")
    void findVisible_type_필터() {
        Long noticeId = service.create(ADMIN, cmd(NoticeType.공지));
        Long eventId = service.create(ADMIN, cmd(NoticeType.이벤트));

        Page<NoticeResult> events = service.findVisible(NoticeType.이벤트, PageRequest.of(0, 10));
        assertThat(events.getContent()).extracting(NoticeResult::id).containsExactly(eventId);
    }

    @Test
    @DisplayName("adminFindAll_미공개도 포함")
    void adminFindAll_미공개포함() {
        Long publishedId = service.create(ADMIN, cmd(NoticeType.공지));
        Long hiddenId = service.create(ADMIN, cmd(NoticeType.공지));
        service.setPublished(hiddenId, false);

        Page<NoticeResult> all = service.adminFindAll(null, PageRequest.of(0, 10));
        assertThat(all.getContent()).extracting(NoticeResult::id).contains(publishedId, hiddenId);
    }
}
