package com.sseulang.domain.item.domain;

import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ItemRepository {

    Optional<Item> findById(Long id);

    

    Optional<Item> findByIdForUpdate(Long id);

    
    Page<Item> search(ItemSearchCriteria criteria, Pageable pageable);

    

    Page<Item> findBySellerIdAndStatus(Long sellerId, ItemStatus status, Pageable pageable);

    Item save(Item item);

    void delete(Item item);

    

    int incrementWishlistCount(Long itemId);

    

    int decrementWishlistCount(Long itemId);

    

    Optional<Integer> getWishlistCount(Long itemId);
}
