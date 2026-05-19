package com.sseulang.domain.category.application;

import com.sseulang.domain.category.application.dto.CategoryNodeResult;
import com.sseulang.domain.category.application.dto.CategoryResult;
import com.sseulang.domain.category.domain.Category;
import com.sseulang.domain.category.domain.CategoryRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class CategoryApplicationService {

    private final CategoryRepository categoryRepository;

    public CategoryApplicationService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    

    public List<CategoryNodeResult> getActiveTree() {
        List<Category> all = categoryRepository.findAllActiveSorted();

        Map<Long, List<CategoryNodeResult>> childrenByParent = new HashMap<>();
        List<CategoryNodeResult> roots = new ArrayList<>();

        for (Category c : all) {
            List<CategoryNodeResult> children = childrenByParent.computeIfAbsent(c.getId(), k -> new ArrayList<>());
            CategoryNodeResult node = new CategoryNodeResult(c.getId(), c.getName(), c.getSortOrder(), children);
            if (c.isRoot()) {
                roots.add(node);
            } else {
                childrenByParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(node);
            }
        }
        return roots;
    }

    

    public List<CategoryResult> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return categoryRepository.searchByKeyword(keyword.trim()).stream()
                .map(c -> new CategoryResult(c.getId(), c.getParentId(), c.getName(), c.getSortOrder()))
                .toList();
    }

    public CategoryResult getById(Long id) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        return new CategoryResult(c.getId(), c.getParentId(), c.getName(), c.getSortOrder());
    }

    

    public void requireExists(Long id) {
        if (id == null) {
            return;
        }
        if (categoryRepository.findById(id).isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }
}
