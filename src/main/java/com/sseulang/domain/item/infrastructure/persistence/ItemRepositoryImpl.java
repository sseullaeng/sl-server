package com.sseulang.domain.item.infrastructure.persistence;

import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ItemRepositoryImpl implements ItemRepository {

    private final ItemJpaRepository jpa;

    public ItemRepositoryImpl(ItemJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Item> findById(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Page<Item> findVisibleLatest(Pageable pageable) {
        return jpa.findVisibleLatest(pageable);
    }

    @Override
    public Item save(Item item) {
        return jpa.save(item);
    }

    @Override
    public void delete(Item item) {
        jpa.delete(item);
    }
}
