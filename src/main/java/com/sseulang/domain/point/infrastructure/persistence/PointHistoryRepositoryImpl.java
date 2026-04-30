package com.sseulang.domain.point.infrastructure.persistence;

import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PointHistoryRepositoryImpl implements PointHistoryRepository {

    private final PointHistoryJpaRepository jpa;

    public PointHistoryRepositoryImpl(PointHistoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PointHistory save(PointHistory history) {
        return jpa.save(history);
    }

    @Override
    public List<PointHistory> findByUserIdOrderByCreatedAtDesc(Long userId) {
        return jpa.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
