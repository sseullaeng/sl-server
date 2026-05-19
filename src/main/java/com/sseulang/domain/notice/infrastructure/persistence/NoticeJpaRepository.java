package com.sseulang.domain.notice.infrastructure.persistence;

import com.sseulang.domain.notice.domain.Notice;
import com.sseulang.domain.notice.domain.NoticeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

interface NoticeJpaRepository extends JpaRepository<Notice, Long> {

    

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
