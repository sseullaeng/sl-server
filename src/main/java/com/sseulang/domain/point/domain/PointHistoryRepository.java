package com.sseulang.domain.point.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PointHistoryRepository {

    PointHistory save(PointHistory history);

    
    List<PointHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    

    Page<PointHistory> findByUserIdAndType(Long userId, PointHistoryType type, Pageable pageable);
}
