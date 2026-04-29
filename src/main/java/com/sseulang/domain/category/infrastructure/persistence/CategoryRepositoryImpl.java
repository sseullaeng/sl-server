package com.sseulang.domain.category.infrastructure.persistence;

import com.sseulang.domain.category.domain.Category;
import com.sseulang.domain.category.domain.CategoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryJpaRepository jpa;

    public CategoryRepositoryImpl(CategoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public List<Category> findAllActiveSorted() {
        return jpa.findAllActiveSorted();
    }
}
