package com.sseulang.domain.point.infrastructure.persistence;

import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface PointHistoryJpaRepository extends JpaRepository<PointHistory, Long> {

    List<PointHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("""
            SELECT h FROM PointHistory h
             WHERE h.userId = :userId AND h.pointType = :type
             ORDER BY h.createdAt DESC, h.id DESC
            """)
    Page<PointHistory> findByUserIdAndType(
            @Param("userId") Long userId,
            @Param("type") PointHistoryType type,
            Pageable pageable);

    @Query("""
            SELECT h FROM PointHistory h
             WHERE h.userId = :userId
             ORDER BY h.createdAt DESC, h.id DESC
            """)
    Page<PointHistory> findByUserId(
            @Param("userId") Long userId,
            Pageable pageable);
}
