package com.sseulang.domain.category.application.dto;

import java.util.List;

/** 카테고리 트리 노드. 자식이 없으면 {@code children} 은 빈 리스트. */
public record CategoryNodeResult(
        Long id,
        String name,
        int sortOrder,
        List<CategoryNodeResult> children
) { }
