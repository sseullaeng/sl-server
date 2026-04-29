package com.sseulang.domain.category.application.dto;

/** 카테고리 단건 평탄 결과 (트리 미포함). */
public record CategoryResult(
        Long id,
        Long parentId,
        String name,
        int sortOrder
) { }
