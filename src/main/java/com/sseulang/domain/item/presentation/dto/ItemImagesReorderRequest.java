package com.sseulang.domain.item.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Item 이미지 순서 변경. imageUrls 가 기존 set 과 정확히 일치해야 함 (중복/누락 X).")
public record ItemImagesReorderRequest(
        @Schema(description = "재배치된 이미지 URL — 첫 번째가 새 썸네일",
                example = "[\"https://cdn.sseulang.com/items/42/b.jpg\",\"https://cdn.sseulang.com/items/42/a.jpg\"]")
        @NotEmpty(message = "imageUrls 는 비어있을 수 없습니다")
        @Size(max = 5)
        List<String> imageUrls
) { }
