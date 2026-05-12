package com.sseulang.domain.item.application;

import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemHashtag;
import com.sseulang.domain.item.domain.ItemRepository;
import com.sseulang.domain.item.domain.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 테스트용 인메모리 fake. id 자동 부여 + status != 삭제 인 것만 검색에 노출.
 *
 * <p>{@code search} 는 prod {@link com.sseulang.domain.item.infrastructure.persistence.ItemQuerydslRepository}
 * 와 동일한 의미로 동작해야 하므로 같은 필터 의미를 흉내 낸다.</p>
 */
public class InMemoryFakeItemRepository implements ItemRepository {

    private final Map<Long, Item> store = new HashMap<>();
    private long sequence = 0;

    @Override
    public Optional<Item> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Item> findByIdForUpdate(Long id) {
        // fake — 락 의미 없음. 실제 동시성 검증은 testcontainers IT 에서.
        return findById(id);
    }

    @Override
    public Page<Item> search(ItemSearchCriteria criteria, Pageable pageable) {
        Stream<Item> stream = store.values().stream()
                .filter(i -> i.getStatus() != ItemStatus.삭제);

        if (criteria.q() != null && !criteria.q().isBlank()) {
            String q = criteria.q().strip().toLowerCase();
            stream = stream.filter(i ->
                    i.getTitle().toLowerCase().contains(q)
                            || i.getDescription().toLowerCase().contains(q));
        }
        if (criteria.categoryId() != null) {
            stream = stream.filter(i -> criteria.categoryId().equals(i.getCategoryId()));
        }
        if (criteria.tradeType() != null) {
            stream = stream.filter(i -> criteria.tradeType() == i.getTradeType());
        }
        if (criteria.minPrice() != null) {
            stream = stream.filter(i -> i.getPrice() >= criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            stream = stream.filter(i -> i.getPrice() <= criteria.maxPrice());
        }
        if (criteria.tag() != null && !criteria.tag().isBlank()) {
            String tag = criteria.tag().strip();
            stream = stream.filter(i -> i.getHashtags().stream()
                    .map(ItemHashtag::getTag).anyMatch(t -> t.equals(tag)));
        }

        List<Item> filtered = stream
                .sorted(Comparator.comparingLong(Item::getId).reversed())
                .toList();

        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }

    @Override
    public Item save(Item item) {
        if (item.getId() == null) {
            long id = ++sequence;
            ReflectionTestUtils.setField(item, "id", id);
        }
        store.put(item.getId(), item);
        return item;
    }

    @Override
    public void delete(Item item) {
        store.remove(item.getId());
    }

    @Override
    public void flush() {
        // in-memory — no-op
    }

    @Override
    public int incrementWishlistCount(Long itemId) {
        Item item = store.get(itemId);
        if (item == null) return 0;
        ReflectionTestUtils.setField(item, "wishlistCount", item.getWishlistCount() + 1);
        return 1;
    }

    @Override
    public int decrementWishlistCount(Long itemId) {
        Item item = store.get(itemId);
        if (item == null || item.getWishlistCount() <= 0) return 0;
        ReflectionTestUtils.setField(item, "wishlistCount", item.getWishlistCount() - 1);
        return 1;
    }

    @Override
    public Optional<Integer> getWishlistCount(Long itemId) {
        Item item = store.get(itemId);
        if (item == null) return Optional.empty();
        return Optional.of(item.getWishlistCount());
    }

    @Override
    public Page<Item> findBySellerIdAndStatus(Long sellerId, ItemStatus status, Pageable pageable) {
        Stream<Item> stream = store.values().stream()
                .filter(i -> sellerId.equals(i.getSellerId()));
        if (status == null) {
            stream = stream.filter(i -> i.getStatus() != ItemStatus.삭제);
        } else {
            stream = stream.filter(i -> i.getStatus() == status);
        }
        List<Item> filtered = stream
                .sorted(Comparator.comparingLong(Item::getId).reversed())
                .toList();
        int start = Math.min((int) pageable.getOffset(), filtered.size());
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
    }
}
