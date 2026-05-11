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

    /**
     * 라운드 12 PR-D — 활성 카테고리 이름 부분일치 검색 (자동완성용).
     * keyword 가 null/blank 이면 빈 리스트.
     */
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

    /**
     * 다른 도메인 ApplicationService 가 카테고리 존재 검증할 때 사용. {@code id} 가 null 이면
     * 검증 스킵 (카테고리 미지정 허용 정책). CLAUDE.md §3.3 의 "다른 도메인 Repository 직접 호출 금지"
     * 룰에 맞춰 외부 도메인이 본 메서드만 의존하게 한다.
     */
    public void requireExists(Long id) {
        if (id == null) {
            return;
        }
        if (categoryRepository.findById(id).isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }
}
