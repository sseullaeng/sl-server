package com.sseulang.domain.category.presentation.dto;

import com.sseulang.domain.category.application.dto.CategoryNodeResult;

import java.util.List;

public record CategoryNodeResponse(
        Long id,
        String name,
        int sortOrder,
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
