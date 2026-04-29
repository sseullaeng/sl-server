package com.sseulang.domain.category.infrastructure.persistence;

import com.sseulang.domain.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Spring Data JPA — {@link CategoryRepositoryImpl} 가 wrapping. 외부에서 직접 import 금지. */
interface CategoryJpaRepository extends JpaRepository<Category, Long> {

    @Query("""
        SELECT c FROM Category c
        WHERE c.active = true
        ORDER BY
          CASE WHEN c.parentId IS NULL THEN 0 ELSE 1 END,
          c.parentId,
          c.sortOrder,
          c.id
    """)
    List<Category> findAllActiveSorted();
}
