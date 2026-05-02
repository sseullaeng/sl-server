package com.sseulang.domain.item.domain;

import com.sseulang.global.common.BaseEntity;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Item Aggregate Root. V1 스키마 {@code items} 매핑.
 *
 * <p>자식 Entity {@link ItemImage} 는 본 Root 메서드를 통해서만 추가/제거. 외부에서 컬렉션
 * 직접 조작 금지({@link #getImages()} 는 unmodifiable view).</p>
 *
 * <p>거래 진행 상태(예약/거래완료) 전이는 transaction 도메인이 트리거하며, 본 Aggregate 는
 * 판매자 본인이 직접 호출 가능한 {@link #updateInfo}, {@link #markAsHidden}, {@link #markAsDeleted},
 * {@link #restore} 만 노출한다.</p>
 */
@Entity
@Table(name = "items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    private static final int MAX_IMAGES = 5;
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int REGION_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "deposit")
    private Long deposit;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_unit", length = 20)
    private RentalUnit rentalUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false)
    private TradeType tradeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ItemStatus status;

    @Column(name = "region", length = REGION_MAX_LENGTH)
    private String region;

    /**
     * 썸네일 image_url denormalize. {@link #addImage}/{@link #clearImages} 시 자동 갱신 — Aggregate
     * 외부에서 직접 set 금지. ItemSummary 응답을 N+1 없이 내려주기 위한 V11 컬럼.
     */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "wishlist_count", nullable = false)
    private int wishlistCount;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private final List<ItemImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<ItemHashtag> hashtags = new ArrayList<>();

    public static Item create(
            Long sellerId,
            Long categoryId,
            String title,
            String description,
            long price,
            Long deposit,
            RentalUnit rentalUnit,
            TradeType tradeType,
            String region
    ) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("sellerId 는 양수여야 합니다");
        }
        if (tradeType == null) {
            throw new IllegalArgumentException("tradeType 은 필수입니다");
        }
        validateTitle(title);
        validateDescription(description);
        if (price < 0) {
            throw new IllegalArgumentException("price 는 0 이상이어야 합니다");
        }
        validateRentalFields(tradeType, deposit, rentalUnit);
        validateRegion(region);

        Item item = new Item();
        item.sellerId = sellerId;
        item.categoryId = categoryId;
        item.title = title;
        item.description = description;
        item.price = price;
        item.deposit = deposit;
        item.rentalUnit = rentalUnit;
        item.tradeType = tradeType;
        item.region = region;
        item.status = ItemStatus.판매중;
        item.viewCount = 0;
        item.wishlistCount = 0;
        return item;
    }

    public void addImage(String imageUrl, int sortOrder, boolean thumbnail) {
        if (images.size() >= MAX_IMAGES) {
            throw new BusinessException(ErrorCode.ITEM_IMAGE_LIMIT_EXCEEDED);
        }
        images.add(new ItemImage(this, imageUrl, sortOrder, thumbnail));
        if (thumbnail) {
            this.thumbnailUrl = imageUrl;
        }
    }

    public void clearImages() {
        images.clear();
        this.thumbnailUrl = null;
    }

    /**
     * 부분 추가 — 기존 이미지 유지하고 imageUrls 순서대로 append. 합산 5장 한도 초과 시 IAE 가 아니라
     * {@link ErrorCode#ITEM_IMAGE_LIMIT_EXCEEDED}. 기존이 비었으면 첫 번째 새 url 이 썸네일.
     */
    public void appendImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        if (images.size() + imageUrls.size() > MAX_IMAGES) {
            throw new BusinessException(ErrorCode.ITEM_IMAGE_LIMIT_EXCEEDED);
        }
        boolean wasEmpty = images.isEmpty();
        int order = images.size();
        for (int i = 0; i < imageUrls.size(); i++) {
            order++;
            boolean isThumbnail = wasEmpty && i == 0;
            images.add(new ItemImage(this, imageUrls.get(i), order, isThumbnail));
            if (isThumbnail) {
                this.thumbnailUrl = imageUrls.get(i);
            }
        }
    }

    /**
     * 단건 제거 — image_url 기준. 미존재 시 ITEM_IMAGE_NOT_FOUND.
     * 제거 후 sortOrder 재정렬 (1..N) + 첫 번째를 썸네일로 재지정. 모두 제거되면 thumbnailUrl=null.
     */
    public void removeImage(String imageUrl) {
        if (imageUrl == null) {
            throw new BusinessException(ErrorCode.ITEM_IMAGE_NOT_FOUND);
        }
        boolean removed = images.removeIf(img -> imageUrl.equals(img.getImageUrl()));
        if (!removed) {
            throw new BusinessException(ErrorCode.ITEM_IMAGE_NOT_FOUND);
        }
        // 남은 이미지에 대해 sortOrder + thumbnail 재계산. ItemImage 는 setter 가 없어 교체.
        List<ItemImage> remaining = new ArrayList<>(images);
        images.clear();
        this.thumbnailUrl = null;
        for (int i = 0; i < remaining.size(); i++) {
            String url = remaining.get(i).getImageUrl();
            boolean isThumbnail = (i == 0);
            images.add(new ItemImage(this, url, i + 1, isThumbnail));
            if (isThumbnail) {
                this.thumbnailUrl = url;
            }
        }
    }

    /**
     * 순서 재배치 — newOrder 가 기존 image url 들과 같은 set (count + elements) 이어야 함. 다르면
     * ITEM_IMAGE_ORDER_MISMATCH. newOrder 첫 번째가 새 썸네일.
     */
    public void reorderImages(List<String> newOrder) {
        if (newOrder == null || newOrder.size() != images.size()) {
            throw new BusinessException(ErrorCode.ITEM_IMAGE_ORDER_MISMATCH);
        }
        Set<String> existingUrls = new HashSet<>();
        for (ItemImage img : images) existingUrls.add(img.getImageUrl());
        Set<String> newUrls = new HashSet<>(newOrder);
        if (!existingUrls.equals(newUrls)) {
            throw new BusinessException(ErrorCode.ITEM_IMAGE_ORDER_MISMATCH);
        }
        if (newUrls.size() != newOrder.size()) {
            // 중복 url
            throw new BusinessException(ErrorCode.ITEM_IMAGE_ORDER_MISMATCH);
        }
        images.clear();
        this.thumbnailUrl = null;
        for (int i = 0; i < newOrder.size(); i++) {
            String url = newOrder.get(i);
            boolean isThumbnail = (i == 0);
            images.add(new ItemImage(this, url, i + 1, isThumbnail));
            if (isThumbnail) {
                this.thumbnailUrl = url;
            }
        }
    }

    /** 외부에 노출되는 이미지 컬렉션은 immutable. 변경은 {@link #addImage} / {@link #clearImages}. */
    public List<ItemImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    /**
     * 해시태그 추가. 동일 태그(대소문자/공백 정규화 후)는 중복으로 간주해 무시 — DB UNIQUE(item_id, tag)
     * 와 정합. 태그 자체 검증(blank, 길이)은 {@link ItemHashtag} 생성자에서 IAE.
     */
    public void addHashtag(String tag) {
        ItemHashtag candidate = new ItemHashtag(this, tag);
        Set<String> existing = new HashSet<>();
        for (ItemHashtag h : hashtags) {
            existing.add(h.getTag());
        }
        if (existing.contains(candidate.getTag())) {
            return;
        }
        hashtags.add(candidate);
    }

    public void clearHashtags() {
        hashtags.clear();
    }

    /** 외부에 노출되는 해시태그 컬렉션은 immutable. */
    public List<ItemHashtag> getHashtags() {
        return Collections.unmodifiableList(hashtags);
    }

    public void updateInfo(
            String title,
            String description,
            long price,
            Long deposit,
            RentalUnit rentalUnit,
            String region
    ) {
        if (!status.isEditable()) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        validateTitle(title);
        validateDescription(description);
        if (price < 0) {
            throw new IllegalArgumentException("price 는 0 이상이어야 합니다");
        }
        validateRentalFields(tradeType, deposit, rentalUnit);
        validateRegion(region);

        this.title = title;
        this.description = description;
        this.price = price;
        this.deposit = deposit;
        this.rentalUnit = rentalUnit;
        this.region = region;
    }

    public void assignCategory(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void markAsHidden() {
        if (status == ItemStatus.삭제) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        this.status = ItemStatus.비공개;
    }

    public void markAsDeleted() {
        this.status = ItemStatus.삭제;
    }

    public void restore() {
        if (status != ItemStatus.비공개) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        this.status = ItemStatus.판매중;
    }

    /**
     * 거래 도메인이 reserve 시 호출. 가이드 §5.2 동시 거래 차단의 핵심 — 이미 예약된 Item 에
     * 다른 거래가 reserve 시도 시 {@link ErrorCode#TRANSACTION_RESERVED_BY_OTHER} 로 거부.
     * 그 외 비활성 상태(거래완료/비공개/삭제)는 {@link ErrorCode#ITEM_INVALID_STATE}.
     */
    public void markAsReserved() {
        if (status == ItemStatus.예약) {
            throw new BusinessException(ErrorCode.TRANSACTION_RESERVED_BY_OTHER);
        }
        if (status != ItemStatus.판매중) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        this.status = ItemStatus.예약;
    }

    /** 거래 도메인이 complete 시 호출. 예약 → 거래완료 만 허용. */
    public void markAsSold() {
        if (status != ItemStatus.예약) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        this.status = ItemStatus.거래완료;
    }

    /** 거래 도메인이 cancel 시 호출. 예약 → 판매중 복원. 가이드 §5.2 — 채팅 재활성화 효과. */
    public void restoreFromReserved() {
        if (status != ItemStatus.예약) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        this.status = ItemStatus.판매중;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.sellerId);
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 은 비어있을 수 없습니다");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("title 은 " + TITLE_MAX_LENGTH + "자를 초과할 수 없습니다");
        }
    }

    private static void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description 은 비어있을 수 없습니다");
        }
    }

    private static void validateRegion(String region) {
        if (region != null && region.length() > REGION_MAX_LENGTH) {
            throw new IllegalArgumentException("region 은 " + REGION_MAX_LENGTH + "자를 초과할 수 없습니다");
        }
    }

    private static void validateRentalFields(TradeType tradeType, Long deposit, RentalUnit rentalUnit) {
        if (tradeType.requiresDeposit()) {
            if (deposit == null || deposit < 0) {
                throw new IllegalArgumentException("대여 거래는 0 이상의 deposit 이 필수입니다");
            }
            if (rentalUnit == null) {
                throw new IllegalArgumentException("대여 거래는 rentalUnit 이 필수입니다");
            }
        } else {
            if (deposit != null) {
                throw new IllegalArgumentException("대여 외 거래에는 deposit 을 지정할 수 없습니다");
            }
            if (rentalUnit != null) {
                throw new IllegalArgumentException("대여 외 거래에는 rentalUnit 을 지정할 수 없습니다");
            }
        }
    }
}
