package com.sseulang.domain.category.presentation.dto;

import com.sseulang.domain.category.application.dto.CategoryResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "카테고리 단건 — parentId null 이면 루트.")
public record CategoryResponse(
        @Schema(example = "5") Long id,
        @Schema(description = "루트면 null") Long parentId,
        @Schema(example = "디지털/가전") String name,
        @Schema(example = "1") int sortOrder
) {
    public static CategoryResponse from(CategoryResult result) {
        return new CategoryResponse(result.id(), result.parentId(), result.name(), result.sortOrder());
    }
}
