package com.sseulang.domain.category.domain;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    Optional<Category> findById(Long id);

    

    List<Category> findAllActiveSorted();

    

    List<Category> searchByKeyword(String keyword);
}
