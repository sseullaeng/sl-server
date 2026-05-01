package com.sseulang.domain.category.presentation;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.presentation.dto.CategoryNodeResponse;
import com.sseulang.domain.category.presentation.dto.CategoryResponse;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Category", description = "카테고리 트리 조회 (공개)")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryApplicationService categoryService;

    public CategoryController(CategoryApplicationService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryNodeResponse>> getTree() {
        List<CategoryNodeResponse> tree = categoryService.getActiveTree().stream()
                .map(CategoryNodeResponse::from)
                .toList();
        return ApiResponse.ok(tree);
    }

    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(CategoryResponse.from(categoryService.getById(id)));
    }
}
