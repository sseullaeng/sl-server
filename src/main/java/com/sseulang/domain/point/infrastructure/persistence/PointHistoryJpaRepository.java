package com.sseulang.domain.point.infrastructure.persistence;

import com.sseulang.domain.point.domain.PointHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA — {@link PointHistoryRepositoryImpl} 가 wrapping. 외부 직접 import 금지. */
interface PointHistoryJpaRepository extends JpaRepository<PointHistory, Long> {

    List<PointHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
