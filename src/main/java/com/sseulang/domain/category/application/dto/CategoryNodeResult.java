package com.sseulang.domain.category.application.dto;

import java.util.List;

public record CategoryNodeResult(
        Long id,
        String name,
        int sortOrder,
        List<CategoryNodeResult> children
) { }
