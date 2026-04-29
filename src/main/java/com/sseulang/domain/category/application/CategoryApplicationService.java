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

    /**
     * 활성 카테고리 전체를 트리로 조립해 반환. 단일 SELECT 결과를 in-memory 로 그룹핑한다.
     * Repository 가 parent NULL → parentId asc → sortOrder asc 순으로 정렬해주므로 1-pass 로 조립 가능.
     */
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

    public CategoryResult getById(Long id) {
        Category c = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        return new CategoryResult(c.getId(), c.getParentId(), c.getName(), c.getSortOrder());
    }
}
