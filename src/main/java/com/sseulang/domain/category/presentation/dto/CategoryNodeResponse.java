package com.sseulang.domain.category.presentation.dto;

import com.sseulang.domain.category.application.dto.CategoryNodeResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "카테고리 트리 노드 — children 재귀 구조. GET /categories 의 응답.")
public record CategoryNodeResponse(
        @Schema(example = "5") Long id,
        @Schema(example = "디지털/가전") String name,
        @Schema(example = "1") int sortOrder,
        List<CategoryNodeResponse> children
) {
    public static CategoryNodeResponse from(CategoryNodeResult node) {
        return new CategoryNodeResponse(
                node.id(),
                node.name(),
                node.sortOrder(),
                node.children().stream().map(CategoryNodeResponse::from).toList()
        );
    }
}
