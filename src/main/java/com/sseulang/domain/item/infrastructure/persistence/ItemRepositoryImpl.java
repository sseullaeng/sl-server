package com.sseulang.domain.item.infrastructure.persistence;

import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemRepository;
import com.sseulang.domain.item.domain.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ItemRepositoryImpl implements ItemRepository {

    private final ItemJpaRepository jpa;
    private final ItemQuerydslRepository querydsl;

    public ItemRepositoryImpl(ItemJpaRepository jpa, ItemQuerydslRepository querydsl) {
        this.jpa = jpa;
        this.querydsl = querydsl;
    }

    @Override
    public Optional<Item> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Item> findByIdForUpdate(Long id) {
        return jpa.findByIdForUpdate(id);
    }

    @Override
    public Page<Item> search(ItemSearchCriteria criteria, Pageable pageable) {
        return querydsl.search(criteria, pageable);
    }

    @Override
    public Page<Item> adminSearch(
            com.sseulang.domain.item.application.dto.AdminItemSearchCriteria criteria,
            java.util.Collection<Long> matchedSellerIds,
            Pageable pageable
    ) {
        return querydsl.adminSearch(criteria, matchedSellerIds, pageable);
    }

    @Override
    public Page<Item> findBySellerIdAndStatus(Long sellerId, ItemStatus status, Pageable pageable) {
        if (status == null) {
            return jpa.findBySellerIdExcludingDeleted(sellerId, pageable);
        }
        return jpa.findBySellerIdAndStatus(sellerId, status, pageable);
    }

    @Override
    public Item save(Item item) {
        return jpa.save(item);
    }

    @Override
    public void delete(Item item) {
        jpa.delete(item);
    }

    @Override
    public void flush() {
        jpa.flush();
    }

    @Override
    public int incrementWishlistCount(Long itemId) {
        return jpa.incrementWishlistCount(itemId);
    }

    @Override
    public int decrementWishlistCount(Long itemId) {
        return jpa.decrementWishlistCount(itemId);
    }

    @Override
    public Optional<Integer> getWishlistCount(Long itemId) {
        return jpa.getWishlistCount(itemId);
    }

    @Override
    public java.util.Map<Long, java.util.List<String>> findHashtagsByItemIds(java.util.Collection<Long> itemIds) {
        return querydsl.findHashtagsByItemIds(itemIds);
    }
}
