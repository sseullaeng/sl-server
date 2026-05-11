package com.sseulang.domain.category.application.dto;

public record CategoryResult(
        Long id,
        Long parentId,
        String name,
        int sortOrder
) { }
