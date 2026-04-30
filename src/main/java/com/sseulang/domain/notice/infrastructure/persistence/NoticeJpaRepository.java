package com.sseulang.domain.notice.infrastructure.persistence;

import com.sseulang.domain.notice.domain.Notice;
import com.sseulang.domain.notice.domain.NoticeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/** Spring Data JPA — {@link NoticeRepositoryImpl} 가 wrapping. 외부 직접 import 금지. */
interface NoticeJpaRepository extends JpaRepository<Notice, Long> {

    /**
     * 사용자 노출 쿼리. type null 이면 전체 type. 윈도우는 startsAt/endsAt 두 축으로 판정 —
     * NULL 이면 그쪽 무제한. 정렬은 pinned 우선, 그다음 최신.
     */
    @Query("""
            SELECT n FROM Notice n
             WHERE n.published = true
               AND (:type IS NULL OR n.type = :type)
               AND (n.startsAt IS NULL OR n.startsAt <= :now)
               AND (n.endsAt IS NULL OR n.endsAt > :now)
             ORDER BY n.pinned DESC, n.id DESC
            """)
    Page<Notice> findVisible(
            @Param("now") LocalDateTime now,
            @Param("type") NoticeType type,
            Pageable pageable
    );

    @Query("""
            SELECT n FROM Notice n
             WHERE (:type IS NULL OR n.type = :type)
             ORDER BY n.id DESC
            """)
    Page<Notice> findAllForAdmin(@Param("type") NoticeType type, Pageable pageable);
}
