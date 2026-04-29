package com.sseulang.domain.item.application.dto;

import com.sseulang.domain.item.domain.ItemImage;

public record ItemImageResult(String imageUrl, int sortOrder, boolean thumbnail) {
    public static ItemImageResult from(ItemImage image) {
        return new ItemImageResult(image.getImageUrl(), image.getSortOrder(), image.isThumbnail());
    }
}
