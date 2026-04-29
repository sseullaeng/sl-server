package com.sseulang.domain.category.domain;

import java.util.List;
import java.util.Optional;

/**
 * Category Aggregate Repository. 도메인 layer 인터페이스 — Spring/JPA 의존 X.
 * 구현은 {@code domain/category/infrastructure/persistence}.
 */
public interface CategoryRepository {

    Optional<Category> findById(Long id);

    /**
     * 활성 카테고리 전부 조회. parent_id 우선(NULL 먼저), sort_order 순. 트리 조립은 호출자가 in-memory 로.
     */
    List<Category> findAllActiveSorted();
}
