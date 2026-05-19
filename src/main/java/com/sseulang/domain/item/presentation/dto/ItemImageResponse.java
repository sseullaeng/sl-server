package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemImageResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "물품 이미지 — 등록 시 임시 폴더에서 정식 폴더(items/{itemId}/) 로 promote 된 url.")
public record ItemImageResponse(
        @Schema(example = "https://cdn.sseulang.com/items/42/abc.jpg") String imageUrl,
        @Schema(example = "1", description = "표시 순서 (1-based)") int sortOrder,
        @Schema(example = "true", description = "썸네일 여부 (sortOrder=1 만 true)") boolean thumbnail
) {
    public static ItemImageResponse from(ItemImageResult r) {
        return new ItemImageResponse(r.imageUrl(), r.sortOrder(), r.thumbnail());
    }
}
