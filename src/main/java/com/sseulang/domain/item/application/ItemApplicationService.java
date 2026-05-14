package com.sseulang.domain.item.application;

import com.sseulang.domain.category.application.CategoryApplicationService;
import com.sseulang.domain.file.domain.PresignedUrlGenerator;
import com.sseulang.domain.user.application.UserApplicationService;
import com.sseulang.domain.item.application.dto.ItemDetailResult;
import com.sseulang.domain.item.application.dto.ItemForTransactionResult;
import com.sseulang.domain.item.application.dto.ItemRegisterCommand;
import com.sseulang.domain.item.application.dto.ItemSearchCriteria;
import com.sseulang.domain.item.application.dto.ItemSummaryResult;
import com.sseulang.domain.item.application.dto.ItemUpdateCommand;
import com.sseulang.domain.item.domain.Item;
import com.sseulang.domain.item.domain.ItemRepository;
import com.sseulang.domain.item.domain.ItemStatus;
import com.sseulang.domain.item.domain.WishlistView;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ItemApplicationService {

    private final ItemRepository itemRepository;
    private final CategoryApplicationService categoryApplicationService;
    private final UserApplicationService userApplicationService;
    private final PresignedUrlGenerator presignedUrlGenerator;
    private final WishlistView wishlistView;
    private final com.sseulang.domain.item.domain.ItemReportView itemReportView;

    public ItemApplicationService(
            ItemRepository itemRepository,
            CategoryApplicationService categoryApplicationService,
            UserApplicationService userApplicationService,
            PresignedUrlGenerator presignedUrlGenerator,
            WishlistView wishlistView,
            com.sseulang.domain.item.domain.ItemReportView itemReportView
    ) {
        this.itemRepository = itemRepository;
        this.categoryApplicationService = categoryApplicationService;
        this.userApplicationService = userApplicationService;
        this.presignedUrlGenerator = presignedUrlGenerator;
        this.wishlistView = wishlistView;
        this.itemReportView = itemReportView;
    }

    @Transactional
    public Long register(ItemRegisterCommand cmd) {
        
        userApplicationService.requireVerified(cmd.sellerId());
        categoryApplicationService.requireExists(cmd.categoryId());
        
        
        validateImageOwnership(cmd.sellerId(), cmd.imageUrls());
        Item item = Item.createMulti(
                cmd.sellerId(), cmd.categoryId(),
                cmd.title(), cmd.description(),
                cmd.tradeTypes(),
                cmd.salePrice(), cmd.rentalPrice(),
                cmd.deposit(), cmd.depositType(), cmd.rentalUnit(),
                cmd.region()
        );
        applyHashtags(item, cmd.hashtags());
        Item saved = itemRepository.save(item);
        
        
        
        List<String> promoted = promoteImageUrls(cmd.sellerId(), saved.getId(), cmd.imageUrls());
        applyImages(saved, promoted);
        return saved.getId();
    }

    @Transactional
    public ItemDetailResult getById(Long id) {
        Item item = findOrThrow(id);
        item.incrementViewCount();
        return ItemDetailResult.from(item);
    }

    

    public Page<ItemSummaryResult> search(ItemSearchCriteria criteria, Pageable pageable, Long viewerId) {
        return enrich(itemRepository.search(criteria, pageable), viewerId);
    }

    
    public Page<ItemSummaryResult> search(ItemSearchCriteria criteria, Pageable pageable) {
        return search(criteria, pageable, null);
    }

    

    public Page<ItemSummaryResult> findMyItems(Long sellerId, ItemStatus status, Pageable pageable) {
        return enrich(itemRepository.findBySellerIdAndStatus(sellerId, status, pageable), sellerId);
    }

    private Page<ItemSummaryResult> enrich(Page<Item> page, Long viewerId) {
        if (page.isEmpty()) {
            return page.map(item -> ItemSummaryResult.from(item, false, java.util.List.of()));
        }
        List<Long> ids = page.getContent().stream().map(Item::getId).toList();
        Set<Long> wishlisted = wishlistView.findWishlistedItemIds(viewerId, ids);
        java.util.Map<Long, java.util.List<String>> tagsByItem = itemRepository.findHashtagsByItemIds(ids);
        return page.map(item -> ItemSummaryResult.from(
                item,
                wishlisted.contains(item.getId()),
                tagsByItem.getOrDefault(item.getId(), java.util.List.of())
        ));
    }

    @Transactional
    public void update(Long id, Long requesterId, ItemUpdateCommand cmd) {
        Item item = findOwnedOrThrow(id, requesterId);

        item.updateInfoMulti(
                cmd.title(), cmd.description(),
                cmd.tradeTypes(),
                cmd.salePrice(), cmd.rentalPrice(),
                cmd.deposit(), cmd.depositType(), cmd.rentalUnit(), cmd.region()
        );

        if (cmd.categoryId() != null) {
            categoryApplicationService.requireExists(cmd.categoryId());
            item.assignCategory(cmd.categoryId());
        }
        if (cmd.imageUrls() != null) {


            validateImageOwnershipForUpdate(item.getSellerId(), item.getId(), cmd.imageUrls());
            item.clearImages();
            itemRepository.flush();

            List<String> promoted = promoteImageUrls(item.getSellerId(), item.getId(), cmd.imageUrls());
            applyImages(item, promoted);
        }
        if (cmd.hashtags() != null) {
            item.clearHashtags();
            itemRepository.flush();
            applyHashtags(item, cmd.hashtags());
        }
    }

    @Transactional
    public void delete(Long id, Long requesterId) {
        Item item = findOwnedOrThrow(id, requesterId);
        item.markAsDeleted();
    }

    

    // 라운드 12 — admin item 목록. q 키워드는 title or sellerId IN matchedUsers, status/tradeType/category 필터.
    public Page<com.sseulang.domain.item.application.dto.AdminItemSummaryResult> adminSearch(
            com.sseulang.domain.item.application.dto.AdminItemSearchCriteria criteria,
            Pageable pageable
    ) {
        java.util.List<Long> matchedSellerIds = java.util.Collections.emptyList();
        if (criteria.q() != null && !criteria.q().isBlank()) {
            matchedSellerIds = userApplicationService.findUserIdsByKeyword(criteria.q().strip(), 200);
        }
        Page<Item> page = itemRepository.adminSearch(criteria, matchedSellerIds, pageable);
        if (page.isEmpty()) return Page.empty(pageable);

        java.util.List<Long> sellerIds = page.getContent().stream().map(Item::getSellerId).distinct().toList();
        java.util.Map<Long, com.sseulang.domain.user.application.UserApplicationService.UserProjection> userMap =
                userApplicationService.findProjectionsByIds(sellerIds);
        java.util.List<Long> itemIds = page.getContent().stream().map(Item::getId).toList();
        java.util.Map<Long, Long> reportMap = itemReportView.countByItemIds(itemIds);

        java.util.List<com.sseulang.domain.item.application.dto.AdminItemSummaryResult> rows = page.getContent().stream()
                .map(it -> com.sseulang.domain.item.application.dto.AdminItemSummaryResult.from(
                        it,
                        java.util.Optional.ofNullable(userMap.get(it.getSellerId()))
                                .map(com.sseulang.domain.user.application.UserApplicationService.UserProjection::nickname).orElse(null),
                        reportMap.getOrDefault(it.getId(), 0L)
                ))
                .toList();

        if (criteria.sort() == com.sseulang.domain.item.application.dto.AdminItemSort.REPORT_DESC) {
            rows = new java.util.ArrayList<>(rows);
            rows.sort(java.util.Comparator.comparingLong(
                    com.sseulang.domain.item.application.dto.AdminItemSummaryResult::reportCount).reversed());
        }
        return new org.springframework.data.domain.PageImpl<>(rows, pageable, page.getTotalElements());
    }

    // 라운드 12 — admin item 상세.
    public com.sseulang.domain.item.application.dto.AdminItemDetailResult adminGetDetail(
            Long itemId,
            java.util.List<com.sseulang.domain.report.domain.UserReport> reports,
            java.util.List<com.sseulang.domain.transaction.domain.Transaction> transactions
    ) {
        Item item = findOrThrow(itemId);
        com.sseulang.domain.item.application.dto.ItemDetailResult detail =
                com.sseulang.domain.item.application.dto.ItemDetailResult.from(item);

        com.sseulang.domain.user.application.UserApplicationService.UserProjection seller =
                userApplicationService.findProjectionsByIds(java.util.List.of(item.getSellerId()))
                        .get(item.getSellerId());

        java.util.List<Long> buyerIds = transactions.stream()
                .map(com.sseulang.domain.transaction.domain.Transaction::getBuyerId)
                .distinct().toList();
        java.util.Map<Long, com.sseulang.domain.user.application.UserApplicationService.UserProjection> buyerMap =
                userApplicationService.findProjectionsByIds(buyerIds);

        java.util.List<com.sseulang.domain.item.application.dto.AdminItemDetailResult.ReportHistoryItem> reportRows =
                reports.stream()
                        .map(r -> new com.sseulang.domain.item.application.dto.AdminItemDetailResult.ReportHistoryItem(
                                r.getId(), r.getReporterId(), r.getReason(),
                                r.getStatus().name(), r.getCreatedAt()
                        ))
                        .toList();

        java.util.List<com.sseulang.domain.item.application.dto.AdminItemDetailResult.TransactionHistoryItem> txRows =
                transactions.stream()
                        .map(t -> new com.sseulang.domain.item.application.dto.AdminItemDetailResult.TransactionHistoryItem(
                                t.getId(), t.getBuyerId(),
                                java.util.Optional.ofNullable(buyerMap.get(t.getBuyerId()))
                                        .map(com.sseulang.domain.user.application.UserApplicationService.UserProjection::nickname)
                                        .orElse(null),
                                t.getStatus().name(), t.getPrice(), t.getCompletedAt()
                        ))
                        .toList();

        return new com.sseulang.domain.item.application.dto.AdminItemDetailResult(
                detail,
                seller != null ? seller.nickname() : null,
                reportRows.size(),
                reportRows,
                txRows
        );
    }

    @Transactional
    public void adminDelete(Long id, Long adminId) {
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if (item.getStatus() == ItemStatus.삭제) {
            
            return;
        }
        item.markAsDeleted();
        org.slf4j.LoggerFactory.getLogger(ItemApplicationService.class)
                .warn("[admin] item soft-deleted itemId={} sellerId={} adminId={}",
                        item.getId(), item.getSellerId(), adminId);
    }

    

    @Transactional
    public List<String> appendImages(Long itemId, Long requesterId, List<String> imageUrls) {
        userApplicationService.requireVerified(requesterId);
        Item item = findOwnedOrThrow(itemId, requesterId);
        if (imageUrls == null || imageUrls.isEmpty()) {
            return collectImageUrls(item);
        }
        validateImageOwnershipForUpdate(item.getSellerId(), item.getId(), imageUrls);
        List<String> promoted = promoteImageUrls(item.getSellerId(), item.getId(), imageUrls);
        item.appendImages(promoted);
        return collectImageUrls(item);
    }

    

    @Transactional
    public List<String> removeImage(Long itemId, Long requesterId, String imageUrl) {
        userApplicationService.requireVerified(requesterId);
        Item item = findOwnedOrThrow(itemId, requesterId);
        item.removeImage(imageUrl);
        
        
        presignedUrlGenerator.delete(imageUrl);
        return collectImageUrls(item);
    }

    

    @Transactional
    public List<String> reorderImages(Long itemId, Long requesterId, List<String> newOrder) {
        userApplicationService.requireVerified(requesterId);
        Item item = findOwnedOrThrow(itemId, requesterId);
        item.reorderImages(newOrder);
        return collectImageUrls(item);
    }

    private static List<String> collectImageUrls(Item item) {
        return item.getImages().stream()
                .map(img -> img.getImageUrl())
                .toList();
    }

    

    public void requireActiveItem(Long id) {
        Item item = findOrThrow(id);
        if (item.getStatus() == ItemStatus.삭제) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
    }

    

    public Long findSellerOfActiveItem(Long id) {
        Item item = findOrThrow(id);
        if (item.getStatus() == ItemStatus.삭제) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (item.getStatus() == ItemStatus.비공개) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        return item.getSellerId();
    }

    

    @Transactional
    public ItemForTransactionResult findActiveForTransaction(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if (item.getStatus() != ItemStatus.판매중) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        boolean isRental = item.getTradeTypes().contains(com.sseulang.domain.item.domain.TradeType.대여);
        Integer depositOriginalPercent = (isRental
                && item.getDepositType() == com.sseulang.domain.item.domain.DepositType.PERCENT
                && item.getDeposit() != null)
                ? item.getDeposit().intValue() : null;
        return new ItemForTransactionResult(
                item.getId(), item.getSellerId(),
                item.getTradeTypes(),
                item.getSalePrice(), item.getRentalPrice(),
                isRental ? item.getDepositType() : null,
                item.computeDepositAmount(),
                depositOriginalPercent,
                isRental ? item.getRentalUnit() : null
        );
    }

    

    @Transactional
    public void markItemAsReserved(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        item.markAsReserved();
    }

    
    @Transactional
    public void markItemAsSold(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        item.markAsSold();
    }

    
    @Transactional
    public void restoreItemFromReserved(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        item.restoreFromReserved();
    }

    

    @Transactional
    public void incrementWishlistCount(Long itemId) {
        itemRepository.incrementWishlistCount(itemId);
    }

    

    @Transactional
    public void decrementWishlistCount(Long itemId) {
        itemRepository.decrementWishlistCount(itemId);
    }

    

    public int getWishlistCount(Long itemId) {
        return itemRepository.getWishlistCount(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
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

    

    private List<String> promoteImageUrls(Long sellerId, Long itemId, List<String> sourceUrls) {
        if (sourceUrls == null || sourceUrls.isEmpty()) {
            return sourceUrls;
        }
        String fromPrefix = "items/" + sellerId + "/";
        String toPrefix = "items/" + itemId + "/";
        List<String> promoted = new ArrayList<>(sourceUrls.size());
        for (String src : sourceUrls) {
            promoted.add(presignedUrlGenerator.promote(src, fromPrefix, toPrefix));
        }
        return promoted;
    }

    

    private static void validateImageOwnership(Long sellerId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        String expectedPrefix = "items/" + sellerId + "/";
        for (String url : imageUrls) {
            if (url == null || !url.contains(expectedPrefix)) {
                throw new BusinessException(ErrorCode.ITEM_FORBIDDEN);
            }
        }
    }

    

    private static void validateImageOwnershipForUpdate(Long sellerId, Long itemId, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        String tempPrefix = "items/" + sellerId + "/";
        String finalPrefix = "items/" + itemId + "/";
        for (String url : imageUrls) {
            if (url == null || (!url.contains(tempPrefix) && !url.contains(finalPrefix))) {
                throw new BusinessException(ErrorCode.ITEM_FORBIDDEN);
            }
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
