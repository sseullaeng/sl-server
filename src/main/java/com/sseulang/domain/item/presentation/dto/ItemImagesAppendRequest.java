package com.sseulang.domain.item.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Item 이미지 부분 추가. 합산 5장 한도 — 초과 시 400 ITEM_IMAGE_LIMIT_EXCEEDED.")
public record ItemImagesAppendRequest(
        @Schema(description = "추가할 이미지 URL — 임시 폴더(items/{userId}/) 또는 정식 폴더(items/{itemId}/) 둘 다 허용",
                example = "[\"https://cdn.sseulang.com/items/100/temp_xxx.jpg\"]")
        @NotEmpty(message = "imageUrls 는 비어있을 수 없습니다")
        @Size(max = 5, message = "한 번에 최대 5장까지 추가 가능합니다")
        List<String> imageUrls
) { }
