package com.sseulang.domain.notice.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Notice Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 구현은 {@code domain/notice/infrastructure/persistence}.
 */
public interface NoticeRepository {

    Notice save(Notice notice);

    Optional<Notice> findById(Long id);

    void deleteById(Long id);

    /**
     * 사용자 노출 — published + 게시 윈도우 안 (startsAt 도달 + endsAt 미도달).
     * is_pinned DESC, created_at DESC 정렬.
     */
    Page<Notice> findVisible(LocalDateTime now, NoticeType type, Pageable pageable);

    /** 관리자 — 전체 또는 type 별 페이징 (created_at DESC). */
    Page<Notice> findAllForAdmin(NoticeType type, Pageable pageable);
}
