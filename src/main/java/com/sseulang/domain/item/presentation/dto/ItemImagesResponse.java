package com.sseulang.domain.item.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Item 의 현재 이미지 URL 목록 — 부분 편집 endpoint 응답. sortOrder 기준 1..N 순서.")
public record ItemImagesResponse(
        @Schema(description = "정렬된 이미지 URL — 첫 번째가 썸네일")
        List<String> imageUrls
) {
    public static ItemImagesResponse of(List<String> urls) {
        return new ItemImagesResponse(urls);
    }
}
