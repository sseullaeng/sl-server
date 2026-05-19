package com.sseulang.domain.item.domain;

import java.util.Collection;
import java.util.Set;

public interface WishlistView {

    

    Set<Long> findWishlistedItemIds(Long userId, Collection<Long> itemIds);
}
