package com.sseulang.domain.point.domain;

import java.util.List;

/**
 * PointHistory Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 구현은 {@code domain/point/infrastructure/persistence}.
 */
public interface PointHistoryRepository {

    PointHistory save(PointHistory history);

    /** 특정 사용자의 history 최신순 조회 (페이징은 추후). */
    List<PointHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
