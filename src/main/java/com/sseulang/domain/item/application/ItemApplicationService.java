package com.sseulang.domain.item.application;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.item.application.dto.ItemDetailResult;
import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.application.dto.ItemSummaryResult;
import com.sseulang.domain.item.application.dto.ItemUpdateCommand;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemRepository;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ItemApplicationService {

    private final ItemRepository itemRepository;
    private final CategoryApplicationService categoryApplicationService;

    public ItemApplicationService(
            ItemRepository itemRepository,
            CategoryApplicationService categoryApplicationService
    ) {
        this.itemRepository = itemRepository;
        this.categoryApplicationService = categoryApplicationService;
    }

    @Transactional
    public Long register(ItemRegisterCommand cmd) {
        categoryApplicationService.requireExists(cmd.categoryId());
        Item item = Item.create(
                cmd.sellerId(), cmd.categoryId(),
                cmd.title(), cmd.description(),
                cmd.price(), cmd.deposit(), cmd.rentalUnit(), cmd.tradeType(),
                cmd.region()
        );
        applyImages(item, cmd.imageUrls());
        applyHashtags(item, cmd.hashtags());
        return itemRepository.save(item).getId();
    }

    @Transactional
    public ItemDetailResult getById(Long id) {
        Item item = findOrThrow(id);
        item.incrementViewCount();
        return ItemDetailResult.from(item);
    }

    public Page<ItemSummaryResult> search(ItemSearchCriteria criteria, Pageable pageable) {
        return itemRepository.search(criteria, pageable).map(ItemSummaryResult::from);
    }

    @Transactional
    public void update(Long id, Long requesterId, ItemUpdateCommand cmd) {
        Item item = findOwnedOrThrow(id, requesterId);

        item.updateInfo(
                cmd.title(), cmd.description(),
                cmd.price(), cmd.deposit(), cmd.rentalUnit(), cmd.region()
        );

        if (cmd.categoryId() != null) {
            categoryApplicationService.requireExists(cmd.categoryId());
            item.assignCategory(cmd.categoryId());
        }
        if (cmd.imageUrls() != null) {
            item.clearImages();
            applyImages(item, cmd.imageUrls());
        }
        if (cmd.hashtags() != null) {
            item.clearHashtags();
            applyHashtags(item, cmd.hashtags());
        }
    }

    @Transactional
    public void delete(Long id, Long requesterId) {
        Item item = findOwnedOrThrow(id, requesterId);
        item.markAsDeleted();
    }

    /**
     * 다른 도메인 ApplicationService 가 "활성 Item 존재"만 검증할 때 사용 — Wishlist 등.
     * status=삭제 는 ITEM_NOT_FOUND. CLAUDE.md §3.3 의 다른 도메인 Repository 직접 호출 금지 룰
     * 정합 — 외부는 본 메서드를 통해서만 Item 검증.
     */
    public void requireActiveItem(Long id) {
        Item item = findOrThrow(id);
        if (item.getStatus() == ItemStatus.삭제) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
    }

    /**
     * {@code wishlist_count} 원자 증가. Wishlist 도메인이 찜 추가 성공 직후 호출.
     */
    @Transactional
    public void incrementWishlistCount(Long itemId) {
        itemRepository.incrementWishlistCount(itemId);
    }

    /**
     * {@code wishlist_count} 원자 감소. 음수 방지는 Repository 쪽 SQL 가드.
     */
    @Transactional
    public void decrementWishlistCount(Long itemId) {
        itemRepository.decrementWishlistCount(itemId);
    }

    private Item findOrThrow(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
    }

    private Item findOwnedOrThrow(Long id, Long requesterId) {
        Item item = findOrThrow(id);
        if (!item.isOwnedBy(requesterId)) {
            throw new BusinessException(ErrorCode.ITEM_FORBIDDEN);
        }
        return item;
    }

    private static void applyImages(Item item, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        int order = 0;
        for (String url : imageUrls) {
            order++;
            item.addImage(url, order, order == 1);
        }
    }

    private static void applyHashtags(Item item, List<String> hashtags) {
        if (hashtags == null || hashtags.isEmpty()) {
            return;
        }
        for (String tag : hashtags) {
            item.addHashtag(tag);
        }
    }
}
