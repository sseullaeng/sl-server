package com.sseulang.domain.item.domain;

import com.sseulang.global.common.BaseEntity;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Column(name = "sale_price")
    private Long salePrice;

    @Column(name = "rental_price")
    private Long rentalPrice;

    @Column(name = "deposit")
    private Long deposit;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_unit", length = 20)
    private RentalUnit rentalUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false)
    private TradeType tradeType;

    @Convert(converter = TradeTypesConverter.class)
    @Column(name = "trade_types", nullable = false, length = 64)
    private Set<TradeType> tradeTypes = EnumSet.noneOf(TradeType.class);

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ItemStatus status;

    @Column(name = "region", length = REGION_MAX_LENGTH)
    private String region;

    

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
        if (tradeType == null) {
            throw new IllegalArgumentException("tradeType 은 필수입니다");
        }
        validateRentalFields(tradeType, deposit, rentalUnit);
        Long salePrice = tradeType == TradeType.판매 ? price : null;
        Long rentalPrice = tradeType == TradeType.대여 ? price : null;
        return createMulti(sellerId, categoryId, title, description,
                EnumSet.of(tradeType), salePrice, rentalPrice, deposit, rentalUnit, region);
    }

    public static Item createMulti(
            Long sellerId,
            Long categoryId,
            String title,
            String description,
            Set<TradeType> tradeTypes,
            Long salePrice,
            Long rentalPrice,
            Long deposit,
            RentalUnit rentalUnit,
            String region
    ) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("sellerId 는 양수여야 합니다");
        }
        validateTitle(title);
        validateDescription(description);
        validateTradeTypes(tradeTypes, salePrice, rentalPrice, deposit, rentalUnit);
        validateRegion(region);

        Set<TradeType> typeSet = EnumSet.copyOf(tradeTypes);
        TradeType primary = primaryOf(typeSet);

        Item item = new Item();
        item.sellerId = sellerId;
        item.categoryId = categoryId;
        item.title = title;
        item.description = description;
        item.tradeTypes = typeSet;
        item.tradeType = primary;
        item.salePrice = typeSet.contains(TradeType.판매) ? salePrice : null;
        item.rentalPrice = typeSet.contains(TradeType.대여) ? rentalPrice : null;
        item.price = derivePrice(primary, item.salePrice, item.rentalPrice);
        item.deposit = typeSet.contains(TradeType.대여) ? deposit : null;
        item.rentalUnit = typeSet.contains(TradeType.대여) ? rentalUnit : null;
        item.region = region;
        item.status = ItemStatus.판매중;
        item.viewCount = 0;
        item.wishlistCount = 0;
        return item;
    }

    private static TradeType primaryOf(Set<TradeType> types) {
        if (types.contains(TradeType.판매)) return TradeType.판매;
        if (types.contains(TradeType.대여)) return TradeType.대여;
        return TradeType.나눔;
    }

    private static long derivePrice(TradeType primary, Long salePrice, Long rentalPrice) {
        return switch (primary) {
            case 판매 -> salePrice != null ? salePrice : 0L;
            case 대여 -> rentalPrice != null ? rentalPrice : 0L;
            case 나눔 -> 0L;
        };
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

    

    public void removeImage(String imageUrl) {
        if (imageUrl == null) {
            throw new BusinessException(ErrorCode.ITEM_IMAGE_NOT_FOUND);
        }
        boolean removed = images.removeIf(img -> imageUrl.equals(img.getImageUrl()));
        if (!removed) {
            throw new BusinessException(ErrorCode.ITEM_IMAGE_NOT_FOUND);
        }
        
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

    
    public List<ItemImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    

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
        Long salePrice = tradeType == TradeType.판매 ? price : null;
        Long rentalPrice = tradeType == TradeType.대여 ? price : null;
        updateInfoMulti(title, description, EnumSet.of(tradeType),
                salePrice, rentalPrice, deposit, rentalUnit, region);
    }

    public void updateInfoMulti(
            String title,
            String description,
            Set<TradeType> tradeTypes,
            Long salePrice,
            Long rentalPrice,
            Long deposit,
            RentalUnit rentalUnit,
            String region
    ) {
        if (!status.isEditable()) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        validateTitle(title);
        validateDescription(description);
        validateTradeTypes(tradeTypes, salePrice, rentalPrice, deposit, rentalUnit);
        validateRegion(region);

        Set<TradeType> typeSet = EnumSet.copyOf(tradeTypes);
        TradeType primary = primaryOf(typeSet);

        this.title = title;
        this.description = description;
        this.tradeTypes = typeSet;
        this.tradeType = primary;
        this.salePrice = typeSet.contains(TradeType.판매) ? salePrice : null;
        this.rentalPrice = typeSet.contains(TradeType.대여) ? rentalPrice : null;
        this.price = derivePrice(primary, this.salePrice, this.rentalPrice);
        this.deposit = typeSet.contains(TradeType.대여) ? deposit : null;
        this.rentalUnit = typeSet.contains(TradeType.대여) ? rentalUnit : null;
        this.region = region;
    }

    public Long priceFor(TradeType mode) {
        if (mode == null) return null;
        return switch (mode) {
            case 판매 -> salePrice;
            case 대여 -> rentalPrice;
            case 나눔 -> 0L;
        };
    }

    public Set<TradeType> getTradeTypes() {
        return Collections.unmodifiableSet(tradeTypes);
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

    

    public void markAsReserved() {
        if (status == ItemStatus.예약) {
            throw new BusinessException(ErrorCode.TRANSACTION_RESERVED_BY_OTHER);
        }
        if (status != ItemStatus.판매중) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        this.status = ItemStatus.예약;
    }

    
    public void markAsSold() {
        if (status != ItemStatus.예약) {
            throw new BusinessException(ErrorCode.ITEM_INVALID_STATE);
        }
        this.status = ItemStatus.거래완료;
    }

    
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

    private static void validateTradeTypes(
            Set<TradeType> tradeTypes,
            Long salePrice,
            Long rentalPrice,
            Long deposit,
            RentalUnit rentalUnit
    ) {
        if (tradeTypes == null || tradeTypes.isEmpty()) {
            throw new IllegalArgumentException("tradeTypes 는 최소 1개 이상이어야 합니다");
        }
        if (tradeTypes.contains(TradeType.판매)) {
            if (salePrice == null || salePrice < 0) {
                throw new IllegalArgumentException("판매 모드는 0 이상의 salePrice 가 필수입니다");
            }
        }
        if (tradeTypes.contains(TradeType.대여)) {
            if (rentalPrice == null || rentalPrice < 0) {
                throw new IllegalArgumentException("대여 모드는 0 이상의 rentalPrice 가 필수입니다");
            }
            if (rentalUnit == null) {
                throw new IllegalArgumentException("대여 모드는 rentalUnit 이 필수입니다");
            }
            if (deposit == null || deposit < 0) {
                throw new IllegalArgumentException("대여 모드는 0 이상의 deposit 이 필수입니다");
            }
        }
    }
}
