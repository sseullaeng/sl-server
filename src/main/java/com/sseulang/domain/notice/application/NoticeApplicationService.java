package com.sseulang.domain.notice.application;

import com.sseulang.domain.notice.application.dto.NoticeResult;
import com.sseulang.domain.notice.application.dto.NoticeUpsertCommand;
import com.sseulang.domain.notice.domain.Notice;
import com.sseulang.domain.notice.domain.NoticeRepository;
import com.sseulang.domain.notice.domain.NoticeType;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 공지 흐름.
 *
 * <ul>
 *   <li>관리자: CRUD + pin/publish 토글.</li>
 *   <li>사용자: 노출 윈도우 ({@code Notice.isVisibleAt}) 조건 만족하는 공지만 조회.
 *       단건 조회 시 view_count 증가.</li>
 * </ul>
 *
 * <p>관리자 권한 검증은 SecurityConfig admin chain ({@code hasRole("ADMIN")}) 에 위임 — 본
 * 서비스에서는 admin id 만 받아 작성자 정보로 기록.</p>
 */
@Service
@Transactional(readOnly = true)
public class NoticeApplicationService {

    private final NoticeRepository noticeRepository;
    private final Clock clock;

    public NoticeApplicationService(NoticeRepository noticeRepository, Clock clock) {
        this.noticeRepository = noticeRepository;
        this.clock = clock;
    }

    @Transactional
    public Long create(Long adminId, NoticeUpsertCommand cmd) {
        Notice n = Notice.create(
                adminId, cmd.type(), cmd.title(), cmd.content(),
                cmd.imageUrl(), cmd.startsAt(), cmd.endsAt()
        );
        return noticeRepository.save(n).getId();
    }

    @Transactional
    public void update(Long noticeId, NoticeUpsertCommand cmd) {
        Notice n = findOrThrow(noticeId);
        n.update(cmd.type(), cmd.title(), cmd.content(),
                cmd.imageUrl(), cmd.startsAt(), cmd.endsAt());
    }

    @Transactional
    public void setPinned(Long noticeId, boolean pinned) {
        Notice n = findOrThrow(noticeId);
        if (pinned) n.pin(); else n.unpin();
    }

    @Transactional
    public void setPublished(Long noticeId, boolean published) {
        Notice n = findOrThrow(noticeId);
        if (published) n.publish(); else n.unpublish();
    }

    @Transactional
    public void delete(Long noticeId) {
        if (noticeRepository.findById(noticeId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
        noticeRepository.deleteById(noticeId);
    }

    /** 사용자 단건 조회 + 조회수 증가. 미공개·윈도우 외 공지는 NOT_FOUND 로 동일 응답 (존재 leak 차단). */
    @Transactional
    public NoticeResult viewById(Long noticeId) {
        Notice n = findOrThrow(noticeId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!n.isVisibleAt(now)) {
            throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
        }
        n.incrementViewCount();
        return NoticeResult.from(n);
    }

    public Page<NoticeResult> findVisible(NoticeType type, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now(clock);
        return noticeRepository.findVisible(now, type, pageable).map(NoticeResult::from);
    }

    public Page<NoticeResult> adminFindAll(NoticeType type, Pageable pageable) {
        return noticeRepository.findAllForAdmin(type, pageable).map(NoticeResult::from);
    }

    public NoticeResult adminFindById(Long noticeId) {
        return NoticeResult.from(findOrThrow(noticeId));
    }

    private Notice findOrThrow(Long id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
    }
}
