package com.sseulang.domain.category.infrastructure.persistence;

import com.sseulang.domain.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    @Query("""
        SELECT c FROM Category c
        WHERE c.active = true
          AND LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY c.sortOrder, c.id
    """)
    List<Category> searchByKeyword(@Param("keyword") String keyword);
}
