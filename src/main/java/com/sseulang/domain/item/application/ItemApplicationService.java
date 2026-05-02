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

    public ItemApplicationService(
            ItemRepository itemRepository,
            CategoryApplicationService categoryApplicationService,
            UserApplicationService userApplicationService,
            PresignedUrlGenerator presignedUrlGenerator,
            WishlistView wishlistView
    ) {
        this.itemRepository = itemRepository;
        this.categoryApplicationService = categoryApplicationService;
        this.userApplicationService = userApplicationService;
        this.presignedUrlGenerator = presignedUrlGenerator;
        this.wishlistView = wishlistView;
    }

    @Transactional
    public Long register(ItemRegisterCommand cmd) {
        // Item 등록 — 사기 방지 위해 이메일 인증 필수 (게이트 1).
        userApplicationService.requireVerified(cmd.sellerId());
        categoryApplicationService.requireExists(cmd.categoryId());
        // presigned URL 발급 시 받은 이미지 키가 본인 ownership prefix(items/{sellerId}/) 인지 검증.
        // 다른 사용자의 임시 키를 본인 Item 으로 등록하는 위변조 차단 (follow-up #12 옵션 B).
        validateImageOwnership(cmd.sellerId(), cmd.imageUrls());
        Item item = Item.create(
                cmd.sellerId(), cmd.categoryId(),
                cmd.title(), cmd.description(),
                cmd.price(), cmd.deposit(), cmd.rentalUnit(), cmd.tradeType(),
                cmd.region()
        );
        applyHashtags(item, cmd.hashtags());
        Item saved = itemRepository.save(item);
        // 등록된 itemId 가 생긴 후, 임시 폴더(items/{userId}/) 의 키를 정식 폴더(items/{itemId}/) 로
        // S3 copy + delete (follow-up #12 옵션 A). 트랜잭션 안에서 실패 시 DB 롤백 + S3 garbage 는
        // lifecycle 정책으로 정리.
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

    /**
     * 공개 검색·필터. viewerId 가 null 이면 비로그인 — isWishlisted 는 항상 false.
     * Page 결과의 itemId 들에 대해 단일 SELECT 로 viewer 의 찜 여부 enrich (N+1 회피).
     */
    public Page<ItemSummaryResult> search(ItemSearchCriteria criteria, Pageable pageable, Long viewerId) {
        return enrich(itemRepository.search(criteria, pageable), viewerId);
    }

    /** viewer 모름 — 비로그인 entry / 시스템 호출용. */
    public Page<ItemSummaryResult> search(ItemSearchCriteria criteria, Pageable pageable) {
        return search(criteria, pageable, null);
    }

    /**
     * 마이페이지용 본인 물품 목록. status null = 삭제 제외 전체. viewer = sellerId 본인이므로
     * 본인이 찜한 자기 물품은 표시 (서비스 정책상 가능). viewerId 명시로 isWishlisted 계산.
     */
    public Page<ItemSummaryResult> findMyItems(Long sellerId, ItemStatus status, Pageable pageable) {
        return enrich(itemRepository.findBySellerIdAndStatus(sellerId, status, pageable), sellerId);
    }

    private Page<ItemSummaryResult> enrich(Page<Item> page, Long viewerId) {
        if (page.isEmpty()) {
            return page.map(item -> ItemSummaryResult.from(item, false));
        }
        List<Long> ids = page.getContent().stream().map(Item::getId).toList();
        Set<Long> wishlisted = wishlistView.findWishlistedItemIds(viewerId, ids);
        return page.map(item -> ItemSummaryResult.from(item, wishlisted.contains(item.getId())));
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
            // 업데이트 시에도 동일 ownership 검증 — sellerId 임시 폴더 또는 이미 promote 된 itemId
            // 정식 폴더 prefix 둘 다 허용 (follow-up #12 옵션 A/B 통합).
            validateImageOwnershipForUpdate(item.getSellerId(), item.getId(), cmd.imageUrls());
            item.clearImages();
            // 새로 업로드된 임시 키는 정식 폴더로 promote, 이미 정식인 키는 promoter 가 no-op.
            List<String> promoted = promoteImageUrls(item.getSellerId(), item.getId(), cmd.imageUrls());
            applyImages(item, promoted);
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
     * 부분 추가 — 본인 + 이메일 인증 + 5장 한도 체크 (도메인). temp 폴더 url 은 promote, 정식 폴더 url 은
     * 그대로 재사용 (no-op). 응답으로 반영 후 전체 image url 리스트 반환.
     */
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

    /**
     * 단건 제거 — 본인 + 미존재 시 404 ITEM_IMAGE_NOT_FOUND. 도메인이 sortOrder/thumbnail 재계산.
     * 트랜잭션 커밋 시점에 S3 delete 시도 (best-effort). 응답으로 남은 image url 리스트 반환.
     */
    @Transactional
    public List<String> removeImage(Long itemId, Long requesterId, String imageUrl) {
        userApplicationService.requireVerified(requesterId);
        Item item = findOwnedOrThrow(itemId, requesterId);
        item.removeImage(imageUrl);
        // S3 delete — DB 반영(트랜잭션 커밋) 후에 호출하는 게 정합성 안전. 여기선 best-effort 라
        // 트랜잭션 안에서 호출해도 무방 (실패해도 삼키므로 롤백 안 됨).
        presignedUrlGenerator.delete(imageUrl);
        return collectImageUrls(item);
    }

    /**
     * 순서 재배치 — newOrder 가 기존 image url 들과 정확히 같은 set 이어야 함. 다르면 400 ORDER_MISMATCH.
     * 첫 번째가 새 썸네일.
     */
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
     * Chat / 거래 시작 등 다른 도메인이 Item 의 seller 를 알아야 할 때.
     * 삭제: ITEM_NOT_FOUND, 비공개: ITEM_INVALID_STATE. 판매중/예약/거래완료 는 채팅 가능.
     */
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

    /**
     * 거래 도메인이 거래 생성 시 호출. 비관적 락(PESSIMISTIC_WRITE)으로 Item 행을 잠근 뒤 활성(판매중) 검증.
     * 가이드 §5.2 — reserve 와 create 동시 시 락 직렬화로 "예약 직후 새 채팅중 거래 저장" 회귀 차단.
     * Codex 게이트 1 Warning 보강.
     */
    @Transactional
    public ItemForTransactionResult findActiveForTransaction(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if (item.getStatus() != ItemStatus.판매중) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        return new ItemForTransactionResult(
                item.getId(), item.getSellerId(), item.getTradeType(), item.getPrice(), item.getDeposit()
        );
    }

    /**
     * 거래 도메인이 reserve 시 호출. 비관적 락(PESSIMISTIC_WRITE)으로 Item 행을 잠그고 markAsReserved 호출.
     * 가이드 §5.2 동시 거래 차단의 핵심 — 같은 트랜잭션 안에서 호출되면 락 유지 + 도메인 invariant 검증.
     */
    @Transactional
    public void markItemAsReserved(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        item.markAsReserved();
    }

    /** 거래 도메인이 complete 시 호출. 비관적 락 + 예약 → 거래완료. */
    @Transactional
    public void markItemAsSold(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        item.markAsSold();
    }

    /** 거래 도메인이 cancel 시 호출 (예약 상태였던 거래만). 예약 → 판매중 복원. */
    @Transactional
    public void restoreItemFromReserved(Long itemId) {
        Item item = itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        item.restoreFromReserved();
    }

    /**
     * {@code wishlist_count} 원자 증가. Wishlist 도메인이 찜 추가 성공 직후 호출.
     *
     * <p><b>Stale 주의(bulk update)</b>: JPA bulk update 라 persistence context 가 자동 동기화되지
     * 않는다. 같은 트랜잭션 안에서 후속으로 동일 {@code Item} 을 다시 읽을 경우 stale 값을 받을 수
     * 있으므로 그때는 {@code EntityManager.refresh} 또는 {@code flush+clear} 필요. 현재 wishlist
     * add/remove 흐름은 호출 직후 read 가 없어 안전.</p>
     */
    @Transactional
    public void incrementWishlistCount(Long itemId) {
        itemRepository.incrementWishlistCount(itemId);
    }

    /**
     * {@code wishlist_count} 원자 감소. 음수 방지는 Repository 쪽 SQL 가드.
     * Stale 주의는 {@link #incrementWishlistCount} 와 동일.
     */
    @Transactional
    public void decrementWishlistCount(Long itemId) {
        itemRepository.decrementWishlistCount(itemId);
    }

    /**
     * Fresh wishlist_count 조회 — bulk update 직후 호출해도 정확한 DB 값. 본인 토글 응답에 즉시 반영용.
     * row 가 없으면 ITEM_NOT_FOUND.
     */
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

    /**
     * 임시 폴더({@code items/{sellerId}/}) 의 객체를 정식 폴더({@code items/{itemId}/}) 로 S3 promote.
     * 이미 정식 폴더에 있는 url 은 promoter 가 no-op (follow-up #12 옵션 A).
     */
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

    /**
     * presigned URL 발급 시 받은 이미지 키가 본인 sellerId 의 prefix 를 가지는지 검증.
     * key 형식: {@code items/{sellerId}/{uuid}.{ext}} (FileApplicationService.buildKey 참조).
     * 다른 사용자의 임시 키를 본인 Item 으로 등록하는 위변조 차단 (follow-up #12).
     */
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

    /**
     * update 전용 — 임시 폴더({@code items/{sellerId}/}) 또는 정식 폴더({@code items/{itemId}/})
     * 둘 다 허용. 정식 폴더는 이전 register 단계에서 promote 된 결과 url (사용자가 그대로 다시 보낸 경우).
     */
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
