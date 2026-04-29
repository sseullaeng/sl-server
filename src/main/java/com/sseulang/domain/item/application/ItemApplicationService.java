package com.sseulang.domain.item.application;

import com.sseulang.domain.category.domain.CategoryRepository;
import com.sseulang.domain.item.application.dto.ItemDetailResult;
import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.application.dto.ItemSummaryResult;
import com.sseulang.domain.item.application.dto.ItemUpdateCommand;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemRepository;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ItemApplicationService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;

    public ItemApplicationService(
            ItemRepository itemRepository,
            CategoryRepository categoryRepository
    ) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Long register(ItemRegisterCommand cmd) {
        validateCategoryExists(cmd.categoryId());
        Item item = Item.create(
                cmd.sellerId(), cmd.categoryId(),
                cmd.title(), cmd.description(),
                cmd.price(), cmd.deposit(), cmd.rentalUnit(), cmd.tradeType(),
                cmd.region()
        );
        applyImages(item, cmd.imageUrls());
        return itemRepository.save(item).getId();
    }

    @Transactional
    public ItemDetailResult getById(Long id) {
        Item item = findOrThrow(id);
        item.incrementViewCount();
        return ItemDetailResult.from(item);
    }

    public Page<ItemSummaryResult> listLatest(Pageable pageable) {
        return itemRepository.findVisibleLatest(pageable).map(ItemSummaryResult::from);
    }

    @Transactional
    public void update(Long id, Long requesterId, ItemUpdateCommand cmd) {
        Item item = findOwnedOrThrow(id, requesterId);

        item.updateInfo(
                cmd.title(), cmd.description(),
                cmd.price(), cmd.deposit(), cmd.rentalUnit(), cmd.region()
        );

        if (cmd.categoryId() != null) {
            validateCategoryExists(cmd.categoryId());
            item.assignCategory(cmd.categoryId());
        }
        if (cmd.imageUrls() != null) {
            item.clearImages();
            applyImages(item, cmd.imageUrls());
        }
    }

    @Transactional
    public void delete(Long id, Long requesterId) {
        Item item = findOwnedOrThrow(id, requesterId);
        item.markAsDeleted();
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

    private void validateCategoryExists(Long categoryId) {
        if (categoryId == null) {
            return;
        }
        if (categoryRepository.findById(categoryId).isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }
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
}
