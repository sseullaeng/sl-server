package com.sseulang.domain.banner.infrastructure.persistence;

import com.sseulang.domain.banner.domain.Banner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

interface BannerJpaRepository extends JpaRepository<Banner, Long> {

    @Query("""
            SELECT b FROM Banner b
             WHERE b.active = true
               AND (b.startsAt IS NULL OR b.startsAt <= :now)
               AND (b.endsAt IS NULL OR b.endsAt > :now)
             ORDER BY b.sortOrder ASC, b.id ASC
            """)
    List<Banner> findVisibleSorted(@Param("now") LocalDateTime now);

    Page<Banner> findAllByOrderByIdDesc(Pageable pageable);
}
