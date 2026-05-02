package com.sseulang.domain.point.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * PointHistory Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 구현은 {@code domain/point/infrastructure/persistence}.
 */
public interface PointHistoryRepository {

    PointHistory save(PointHistory history);

    /** 특정 사용자의 history 최신순 조회 (전수). admin/리포팅 용. */
    List<PointHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 본인 history 페이징 — 마이페이지 노출용. type 이 null 이면 전체 type, 명시 시 정확 일치.
     * 정렬: createdAt DESC + id DESC (안정 정렬).
     */
    Page<PointHistory> findByUserIdAndType(Long userId, PointHistoryType type, Pageable pageable);
}
