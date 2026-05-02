package com.sseulang.domain.point.infrastructure.persistence;

import com.sseulang.domain.point.domain.PointHistory;
import com.sseulang.domain.point.domain.PointHistoryRepository;
import com.sseulang.domain.point.domain.PointHistoryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Override
    public Page<PointHistory> findByUserIdAndType(Long userId, PointHistoryType type, Pageable pageable) {
        if (type == null) {
            return jpa.findByUserId(userId, pageable);
        }
        return jpa.findByUserIdAndType(userId, type, pageable);
    }
}
