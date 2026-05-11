package com.sseulang.domain.item.application.dto;

public enum ItemSort {
    
    LATEST,
    
    PRICE_ASC,
    
    PRICE_DESC,
    
    VIEW_DESC,
    
    WISHLIST_DESC;

    public static ItemSort parse(String raw) {
        if (raw == null || raw.isBlank()) return LATEST;
        try {
            return ItemSort.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LATEST;
        }
    }
}
