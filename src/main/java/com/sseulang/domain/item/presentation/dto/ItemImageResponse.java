package com.sseulang.domain.item.presentation.dto;

import com.sseulang.domain.item.application.dto.ItemImageResult;

public record ItemImageResponse(String imageUrl, int sortOrder, boolean thumbnail) {
    public static ItemImageResponse from(ItemImageResult r) {
        return new ItemImageResponse(r.imageUrl(), r.sortOrder(), r.thumbnail());
    }
}
