package com.sseulang.domain.category.presentation.dto;

import com.sseulang.domain.category.application.dto.CategoryResult;

public record CategoryResponse(
        Long id,
        Long parentId,
        String name,
        int sortOrder
) {
    public static CategoryResponse from(CategoryResult result) {
        return new CategoryResponse(result.id(), result.parentId(), result.name(), result.sortOrder());
    }
}
