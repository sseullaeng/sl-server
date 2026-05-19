package com.sseulang.domain.category.presentation;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.category.presentation.dto.CategoryNodeResponse;
import com.sseulang.domain.category.presentation.dto.CategoryResponse;
import com.sseulang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "카테고리 트리 조회 (공개)",
            description = "활성 카테고리 전체 트리. 루트부터 children 재귀.")
    @GetMapping
    public ApiResponse<List<CategoryNodeResponse>> getTree() {
        List<CategoryNodeResponse> tree = categoryService.getActiveTree().stream()
                .map(CategoryNodeResponse::from)
                .toList();
        return ApiResponse.ok(tree);
    }

    @Operation(summary = "카테고리 자동완성 검색 (공개)",
            description = "활성 카테고리 이름 부분일치 (LIKE). keyword 가 비어있으면 빈 배열. 라운드 12 PR-D.")
    @GetMapping("/search")
    public ApiResponse<List<CategoryResponse>> search(@RequestParam("keyword") String keyword) {
        List<CategoryResponse> list = categoryService.searchByKeyword(keyword).stream()
                .map(CategoryResponse::from)
                .toList();
        return ApiResponse.ok(list);
    }

    @Operation(summary = "카테고리 단건 조회 (공개)")
    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getOne(@PathVariable("id") Long id) {
        return ApiResponse.ok(CategoryResponse.from(categoryService.getById(id)));
    }
}
